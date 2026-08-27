package com.dpom.agent.web.authority;

import com.dpom.agent.core.authority.AuthorityId;
import com.dpom.agent.core.authority.InvestigationAuthority;
import com.dpom.agent.core.authority.InvestigationAuthorityStore;
import com.dpom.agent.core.diagnosissource.DiagnosisTerminalCommitService;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.persistence.authority.AuthorityTerminalDao;
import com.dpom.agent.core.persistence.authority.AuthorityRevisionRow;
import com.dpom.agent.core.persistence.authority.DiagnosisSourceRow;
import com.dpom.agent.core.persistence.authority.InvestigationAuthorityDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** H2 与真实 MySQL 共用的 Investigation 权威持久化契约。 */
abstract class AbstractAuthorityPersistenceContract {

    private static final List<String> TABLES = List.of(
            "authority_publication_intent", "authority_diagnosis_source",
            "authority_audit", "authority_tool_use", "authority_investigation_revision",
            "authority_investigation_head");

    @Autowired
    InvestigationAuthorityStore store;

    @Autowired
    InvestigationAuthorityDao dao;

    @Autowired
    AuthorityTerminalDao terminalDao;

    @Autowired
    DiagnosisTerminalCommitService terminalCommitService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    /** 返回当前数据库方言的受控建表脚本。 */
    abstract Resource schemaResource();

    /** 在受控建表前执行方言专用准备，默认不做破坏性操作。 */
    void beforeSchema() {
    }

    @BeforeEach
    void resetAuthorityTables() {
        beforeSchema();
        new ResourceDatabasePopulator(schemaResource()).execute(jdbcTemplate.getDataSource());
        TABLES.forEach(table -> jdbcTemplate.execute("DELETE FROM " + table));
    }

    @Test
    void insertAndUniqueIdentity() {
        InvestigationAuthority authority = newAuthority();
        store.create(authority);

        assertThat(store.find(authority.snapshot().investigationId())).isPresent();
        assertThatThrownBy(() -> store.create(authority)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void optimisticLockAllowsOneWriter() {
        InvestigationAuthority authority = newAuthority();
        store.create(authority);
        InvestigationAuthority first = store.find(authority.snapshot().investigationId()).orElseThrow();
        InvestigationAuthority stale = store.find(authority.snapshot().investigationId()).orElseThrow();
        Instant next = authority.snapshot().createdAt().plusSeconds(1);
        first.transition(0L, InvestigationStatus.SCOPING, next);
        stale.transition(0L, InvestigationStatus.SCOPING, next);

        store.save(first, 0L);

        assertThatThrownBy(() -> store.save(stale, 0L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AUTHORITY_VERSION_CONFLICT");
    }

    @Test
    void transactionRollsBackHeadWhenRevisionInsertFails() {
        InvestigationAuthority authority = newAuthority();
        store.create(authority);
        Instant createdAt = authority.snapshot().createdAt();
        authority.transition(0L, InvestigationStatus.SCOPING, createdAt.plusSeconds(1));
        store.save(authority, 0L);
        authority.transition(1L, InvestigationStatus.RESEARCHING, createdAt.plusSeconds(2));
        String id = authority.snapshot().investigationId().value();
        dao.insertRevision(new AuthorityRevisionRow(id, 2L, "RESEARCHING", "{}",
                "a".repeat(64), LocalDateTime.ofInstant(createdAt.plusSeconds(2), java.time.ZoneOffset.UTC)));

        assertThatThrownBy(() -> store.save(authority, 1L)).isInstanceOf(DuplicateKeyException.class);
        assertThat(store.find(authority.snapshot().investigationId()).orElseThrow().version()).isEqualTo(1L);
    }

    @Test
    void reconstructsExactHistoryAndResumesAfterInterruption() {
        InvestigationAuthority authority = newAuthority();
        AuthorityId id = authority.snapshot().investigationId();
        Instant createdAt = authority.snapshot().createdAt();
        store.create(authority);
        authority.transition(0L, InvestigationStatus.SCOPING, createdAt.plusSeconds(1));
        store.save(authority, 0L);

        InvestigationAuthority resumed = store.find(id).orElseThrow();
        resumed.transition(1L, InvestigationStatus.RESEARCHING, createdAt.plusSeconds(2));
        store.save(resumed, 1L);

        assertThat(store.history(id)).extracting(InvestigationAuthority.Snapshot::version)
                .containsExactly(0L, 1L, 2L);
        assertThat(store.find(id).orElseThrow().snapshot()).isEqualTo(resumed.snapshot());
        assertThat(dao.findAuditPage(id.value(), 0L, 2))
                .extracting(com.dpom.agent.core.persistence.authority.AuthorityAuditViewRow::sequenceNumber)
                .containsExactly(1L, 2L);
        assertThat(dao.findAuditPage(id.value(), 2L, 2))
                .extracting(com.dpom.agent.core.persistence.authority.AuthorityAuditViewRow::sequenceNumber)
                .containsExactly(3L);
    }

    @Test
    void persistsBoundedSuccessAndUnavailableToolUseWithoutBodies() {
        InvestigationAuthority authority = newAuthority();
        Instant createdAt = authority.snapshot().createdAt();
        store.create(authority);
        authority.transition(0L, InvestigationStatus.SCOPING, createdAt.plusSeconds(1));
        authority.startRun(1L, "model@1", "prompt@1", "toolset@1", createdAt.plusSeconds(2));
        InvestigationAuthority.EvidenceReference evidence = new InvestigationAuthority.EvidenceReference(
                "EVID-1", "APM_TREND", "huawei-apm-v1", "obs://evidence/artifact-1", "e".repeat(64));
        authority.recordToolUse(2L, new InvestigationAuthority.ToolUseCommand(
                "query_apm_trend", "1.0.0", "a".repeat(64), List.of("from", "metric", "to"),
                384, "huawei:cn-north-9:apm:instance-1", "correlation-success",
                InvestigationAuthority.ToolUseStatus.SUCCEEDED, null, List.of(evidence)),
                createdAt.plusSeconds(3));
        store.save(authority, 0L);
        authority.recordToolUse(3L, new InvestigationAuthority.ToolUseCommand(
                "query_apm_trace", "1.0.0", "b".repeat(64), List.of("from", "to"),
                128, "huawei:cn-north-9:apm:instance-1", "correlation-timeout",
                InvestigationAuthority.ToolUseStatus.UNAVAILABLE, "UPSTREAM_TIMEOUT", List.of()),
                createdAt.plusSeconds(4));
        store.save(authority, 3L);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM authority_tool_use", Integer.class))
                .isEqualTo(2);
        String success = jdbcTemplate.queryForObject(
                "SELECT CONCAT(status, '|', argument_names_json, '|', evidence_references_json) "
                        + "FROM authority_tool_use WHERE correlation_id = 'correlation-success'", String.class);
        String unavailable = jdbcTemplate.queryForObject(
                "SELECT CONCAT(status, '|', reason_code, '|', evidence_references_json) "
                        + "FROM authority_tool_use WHERE correlation_id = 'correlation-timeout'", String.class);
        assertThat(success).contains("SUCCEEDED", "metric", "EVID-1")
                .doesNotContain("providerEnvelope").doesNotContain("password");
        assertThat(unavailable).isEqualTo("UNAVAILABLE|UPSTREAM_TIMEOUT|[]");
    }

    @Test
    void atomicallyCommitsTerminalSourceAndPublicationIntent() {
        InvestigationAuthority authority = terminalAuthority();
        AuthorityId id = authority.snapshot().investigationId();
        InvestigationAuthority initial = newAuthority(id, authority.snapshot().incident());
        store.create(initial);

        var source = terminalCommitService.commit(authority, 0L);

        assertThat(source.sourceDigest()).matches("[0-9a-f]{64}");
        assertThat(terminalDao.findSource(id.value())).isPresent();
        assertThat(terminalDao.countPendingIntents(id.value())).isOne();
        assertThat(store.find(id).orElseThrow().snapshot()).isEqualTo(authority.snapshot());
    }

    @Test
    void invalidTerminalInvariantCreatesNoSourceOrIntent() {
        InvestigationAuthority authority = newAuthority();
        AuthorityId id = authority.snapshot().investigationId();
        store.create(authority);
        authority.transition(0L, InvestigationStatus.SCOPING,
                authority.snapshot().createdAt().plusSeconds(1));

        assertThatThrownBy(() -> terminalCommitService.commit(authority, 0L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DIAGNOSIS_SOURCE_TERMINAL_REQUIRED");
        assertThat(store.find(id).orElseThrow().version()).isZero();
        assertThat(terminalDao.findSource(id.value())).isEmpty();
        assertThat(terminalDao.countPendingIntents(id.value())).isZero();
    }

    @Test
    void sourcePersistenceFailureRollsBackTerminalStateAndCreatesNoIntent() {
        InvestigationAuthority authority = terminalAuthority();
        AuthorityId id = authority.snapshot().investigationId();
        InvestigationAuthority initial = newAuthority(id, authority.snapshot().incident());
        store.create(initial);
        AuthorityId sourceId = AuthorityId.derive("diagnosis-source", id.value(),
                Long.toString(authority.version()));
        terminalDao.insertSource(new DiagnosisSourceRow(sourceId.value(), id.value(), authority.version(),
                "diagnosis-source/v1", "{}", "a".repeat(64), "b".repeat(64),
                LocalDateTime.ofInstant(authority.snapshot().updatedAt(), java.time.ZoneOffset.UTC)));

        assertThatThrownBy(() -> terminalCommitService.commit(authority, 0L))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(store.find(id).orElseThrow().version()).isZero();
        assertThat(terminalDao.countPendingIntents(id.value())).isZero();
    }

    @Test
    void concurrentTerminalizationCommitsExactlyOneConclusion() throws Exception {
        InvestigationAuthority authority = newAuthority();
        AuthorityId id = authority.snapshot().investigationId();
        Instant createdAt = authority.snapshot().createdAt();
        store.create(authority);
        authority.transition(0L, InvestigationStatus.SCOPING, createdAt.plusSeconds(1));
        authority.transition(1L, InvestigationStatus.SYNTHESIZING, createdAt.plusSeconds(2));
        store.save(authority, 0L);
        InvestigationAuthority first = store.find(id).orElseThrow();
        InvestigationAuthority second = store.find(id).orElseThrow();
        first.conclude(2L, InvestigationAuthority.ConclusionDisposition.HYPOTHESIS,
                "candidate-one", List.of(), List.of(), List.of("runtime evidence"),
                createdAt.plusSeconds(3));
        second.conclude(2L, InvestigationAuthority.ConclusionDisposition.HYPOTHESIS,
                "candidate-two", List.of(), List.of(), List.of("runtime evidence"),
                createdAt.plusSeconds(3));

        assertThat(raceSaves(first, second, 2L)).isEqualTo(1);
        InvestigationAuthority.Snapshot winner = store.find(id).orElseThrow().snapshot();
        assertThat(winner.version()).isEqualTo(3L);
        assertThat(winner.conclusion()).isNotNull();
        assertThat(store.history(id)).hasSize(3);
    }

    private int raceSaves(InvestigationAuthority first, InvestigationAuthority second,
            long expectedVersion) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Boolean> firstResult = pool.submit(() -> saveAfterSignal(first, expectedVersion, ready, start));
            Future<Boolean> secondResult = pool.submit(() -> saveAfterSignal(second, expectedVersion, ready, start));
            ready.await();
            start.countDown();
            return (firstResult.get() ? 1 : 0) + (secondResult.get() ? 1 : 0);
        }
    }

    private boolean saveAfterSignal(InvestigationAuthority authority, long expectedVersion,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            store.save(authority, expectedVersion);
            return true;
        } catch (IllegalStateException | DuplicateKeyException expected) {
            return false;
        }
    }

    private static InvestigationAuthority newAuthority() {
        Instant createdAt = Instant.parse("2026-08-27T01:00:00Z");
        String nonce = UUID.randomUUID().toString();
        AuthorityId incidentId = AuthorityId.derive("incident", nonce);
        InvestigationAuthority.IncidentState incident = new InvestigationAuthority.IncidentState(
                incidentId, "contract-service", "test", "2026.08", "commit-contract",
                "bounded contract symptom", createdAt);
        return InvestigationAuthority.create(incident,
                AuthorityId.derive("investigation", incidentId.value(), "attempt-1"),
                new InvestigationAuthority.BudgetPolicy(30, 30, Duration.ofMinutes(10), 3), createdAt);
    }

    private static InvestigationAuthority newAuthority(AuthorityId investigationId,
            InvestigationAuthority.IncidentState incident) {
        return InvestigationAuthority.create(incident, investigationId,
                new InvestigationAuthority.BudgetPolicy(30, 30, Duration.ofMinutes(10), 3),
                incident.createdAt());
    }

    private static InvestigationAuthority terminalAuthority() {
        InvestigationAuthority authority = newAuthority();
        Instant at = authority.snapshot().createdAt();
        authority.transition(0L, InvestigationStatus.SCOPING, at.plusSeconds(1));
        authority.startRun(1L, "deepseek@1", "prompt@1", "tools@1", at.plusSeconds(2));
        AuthorityId step = authority.appendStep(2L, "EVIDENCE_COLLECTION",
                "collect bounded evidence", at.plusSeconds(3));
        AuthorityId observation = authority.appendObservation(3L, step, "dpom-base",
                "obs://evidence/artifact-1", "e".repeat(64), "bounded evidence", at.plusSeconds(4));
        authority.transition(4L, InvestigationStatus.SYNTHESIZING, at.plusSeconds(5));
        authority.conclude(5L, InvestigationAuthority.ConclusionDisposition.CONFIRMED,
                "bounded confirmed cause", List.of(observation), List.of("alternative"), List.of(),
                at.plusSeconds(6));
        return authority;
    }
}
