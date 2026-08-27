package com.dpom.agent.web.authority;

import com.dpom.agent.common.diagnosisevent.DeliveryAcknowledgement;
import com.dpom.agent.common.diagnosisevent.DeliveryOutcome;
import com.dpom.agent.core.authority.AuthorityId;
import com.dpom.agent.core.authority.InvestigationAuthority;
import com.dpom.agent.core.authority.InvestigationAuthorityStore;
import com.dpom.agent.core.diagnosisevent.AuthorityPublicationDeliveryService;
import com.dpom.agent.core.diagnosisevent.DiagnosisDeliveryPolicy;
import com.dpom.agent.core.diagnosisprogress.AuthorityProgressDeliveryService;
import com.dpom.agent.core.diagnosissource.DiagnosisTerminalCommitService;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.persistence.authority.AuthorityTerminalDao;
import com.dpom.agent.core.persistence.authority.AuthorityProgressDao;
import com.dpom.agent.core.persistence.authority.AuthorityRevisionRow;
import com.dpom.agent.core.persistence.authority.DiagnosticReportRevisionRow;
import com.dpom.agent.core.persistence.authority.DiagnosisSourceRow;
import com.dpom.agent.core.persistence.authority.InvestigationAuthorityDao;
import com.dpom.agent.core.persistence.authority.AuthorityDiagnosticReportDao;
import com.dpom.agent.core.report.DiagnosisOnlyReportCommand;
import com.dpom.agent.core.report.DiagnosisOnlyReportService;
import com.dpom.agent.core.report.DiagnosticReportValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** H2 与真实 MySQL 共用的 Investigation 权威持久化契约。 */
abstract class AbstractAuthorityPersistenceContract {

    private static final List<String> TABLES = List.of(
            "authority_diagnostic_report_head", "authority_diagnostic_report_revision",
            "authority_progress_attempt", "authority_progress_intent",
            "authority_publication_attempt", "authority_publication_intent", "authority_diagnosis_source",
            "authority_audit", "authority_tool_use", "authority_investigation_revision",
            "authority_investigation_head");

    @Autowired
    InvestigationAuthorityStore store;

    @Autowired
    InvestigationAuthorityDao dao;

    @Autowired
    AuthorityTerminalDao terminalDao;

    @Autowired
    AuthorityProgressDao progressDao;

    @Autowired
    DiagnosisTerminalCommitService terminalCommitService;

    @Autowired
    DiagnosisOnlyReportService reportService;

    @Autowired
    AuthorityDiagnosticReportDao reportDao;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void diagnosisOnlyReportIsIdempotentImmutableAndRevisionedFromTerminalFacts() throws Exception {
        InvestigationAuthority authority = terminalAuthority();
        AuthorityId investigationId = authority.snapshot().investigationId();
        store.create(newAuthority(investigationId, authority.snapshot().incident()));
        terminalCommitService.commit(authority, 0L);

        var first = reportService.create(new DiagnosisOnlyReportCommand(investigationId.value(),
                "report-request-1", 0, List.of()));
        var duplicate = reportService.create(new DiagnosisOnlyReportCommand(investigationId.value(),
                "report-request-1", 0, List.of()));
        assertThat(duplicate.reportId()).isEqualTo(first.reportId());
        assertThat(duplicate.requestFingerprint()).isEqualTo(first.requestFingerprint());
        assertThat(duplicate.canonicalContent()).isEqualTo(first.canonicalContent());
        var firstJson = objectMapper.readTree(first.canonicalContent());
        new DiagnosticReportValidator(objectMapper).validate(firstJson);
        assertThat(firstJson.path("reportProfile").asText()).isEqualTo("DIAGNOSIS_ONLY");
        assertThat(firstJson.path("recommendations")).isEmpty();
        assertThat(firstJson.toString()).doesNotContain("alternative");
        assertThat(firstJson.path("evaluation").path("outcome").asText()).isEqualTo("NOT_REQUIRED");

        assertThatThrownBy(() -> reportService.create(new DiagnosisOnlyReportCommand(investigationId.value(),
                "report-request-1", 0, List.of("RECOVERY"))))
                .isInstanceOf(IllegalStateException.class).hasMessage("REPORT_IDEMPOTENCY_CONFLICT");
        var second = reportService.create(new DiagnosisOnlyReportCommand(investigationId.value(),
                "report-request-2", 1, List.of("ALARM_LIFECYCLE_RECOVERED")));
        assertThat(second.revisionNumber()).isEqualTo(2);
        assertThat(second.supersedesReportId()).isEqualTo(first.reportId());
        assertThat(reportService.find(first.reportId()).canonicalContent()).isEqualTo(first.canonicalContent());
        assertThat(reportService.history(investigationId.value(), 0, 1)).extracting("revisionNumber")
                .containsExactly(1L);
        assertThat(reportService.history(investigationId.value(), 1, 10)).extracting("revisionNumber")
                .containsExactly(2L);
        new DiagnosticReportValidator(objectMapper).validateRevisionChain(List.of(firstJson,
                objectMapper.readTree(second.canonicalContent())));

        assertThatThrownBy(() -> reportService.create(new DiagnosisOnlyReportCommand(investigationId.value(),
                "report-request-stale", 1, List.of("RETRY"))))
                .isInstanceOf(IllegalStateException.class).hasMessage("REPORT_VERSION_CONFLICT");
    }

    @Test
    void concurrentReportRevisionAllowsExactlyOneWriterAndKeepsExactHistory() throws Exception {
        InvestigationAuthority authority = terminalAuthority();
        AuthorityId investigationId = authority.snapshot().investigationId();
        store.create(newAuthority(investigationId, authority.snapshot().incident()));
        terminalCommitService.commit(authority, 0L);
        reportService.create(new DiagnosisOnlyReportCommand(investigationId.value(), "initial", 0, List.of()));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Boolean> one = pool.submit(() -> createReportAfterSignal(investigationId, "race-one", ready, start));
            Future<Boolean> two = pool.submit(() -> createReportAfterSignal(investigationId, "race-two", ready, start));
            ready.await();
            start.countDown();
            assertThat((one.get() ? 1 : 0) + (two.get() ? 1 : 0)).isOne();
        }
        assertThat(reportDao.findHead(investigationId.value()).orElseThrow().latestRevision()).isEqualTo(2);
        assertThat(reportService.history(investigationId.value(), 0, 10)).hasSize(2);
    }

    @Test
    void diagnosticReportRevisionRollsBackWithItsTransaction() {
        InvestigationAuthority authority = terminalAuthority();
        AuthorityId investigationId = authority.snapshot().investigationId();
        store.create(newAuthority(investigationId, authority.snapshot().incident()));
        terminalCommitService.commit(authority, 0L);
        var first = reportService.create(new DiagnosisOnlyReportCommand(investigationId.value(),
                "rollback-initial", 0, List.of()));
        String rolledBackId = AuthorityId.derive("diagnostic-report-rollback", investigationId.value()).value();
        var candidate = new DiagnosticReportRevisionRow(rolledBackId, investigationId.value(),
                first.diagnosisSourceId(), "rollback-candidate", "f".repeat(64), 2,
                first.reportId(), "[\"ROLLBACK_PROBE\"]", first.canonicalContent(),
                first.reportDigest(), first.sourceDigest(), LocalDateTime.now());

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            reportDao.insertRevision(candidate);
            throw new IllegalStateException("ROLLBACK_PROBE");
        })).isInstanceOf(IllegalStateException.class).hasMessage("ROLLBACK_PROBE");
        assertThat(reportDao.findRevision(rolledBackId)).isEmpty();
        assertThat(reportDao.findHead(investigationId.value()).orElseThrow().latestRevision()).isOne();
    }

    private boolean createReportAfterSignal(AuthorityId investigationId, String requestKey,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            reportService.create(new DiagnosisOnlyReportCommand(investigationId.value(), requestKey, 1,
                    List.of("CONCURRENT_REVISION")));
            return true;
        } catch (RuntimeException expected) {
            return false;
        }
    }

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
    void progressIntentConflictRollsBackHeadRevisionAndAuditTogether() {
        InvestigationAuthority authority = newAuthority();
        store.create(authority);
        Instant createdAt = authority.snapshot().createdAt();
        authority.transition(0L, InvestigationStatus.SCOPING, createdAt.plusSeconds(1));
        InvestigationAuthority.AuditRecord nextAudit = authority.snapshot().audit().get(1);
        String conflictingProgressId = UUID.nameUUIDFromBytes(
                ("dpom-progress:" + nextAudit.id().value()).getBytes(StandardCharsets.UTF_8)).toString();
        jdbcTemplate.update("UPDATE authority_progress_intent SET progress_id=?, idempotency_key=?",
                conflictingProgressId, conflictingProgressId);

        assertThatThrownBy(() -> store.save(authority, 0L)).isInstanceOf(DuplicateKeyException.class);

        assertThat(store.find(authority.snapshot().investigationId()).orElseThrow().version()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM authority_investigation_revision",
                Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM authority_audit", Integer.class)).isOne();
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
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM authority_progress_intent WHERE investigation_id=?", Integer.class,
                id.value())).isEqualTo(3);
        String admission = jdbcTemplate.queryForObject(
                "SELECT canonical_content FROM authority_progress_intent "
                        + "WHERE investigation_id=? AND progress_sequence=1", String.class, id.value());
        assertThat(admission).contains("\"aggregateVersion\":0", "\"status\":\"ACCEPTED\"")
                .doesNotContain("\"runId\"").doesNotContain("password");
    }

    @Test
    void legacyInvestigationWithoutAdmissionDoesNotJoinProgressStreamMidSequence() {
        InvestigationAuthority authority = newAuthority();
        AuthorityId id = authority.snapshot().investigationId();
        Instant createdAt = authority.snapshot().createdAt();
        store.create(authority);
        jdbcTemplate.update("DELETE FROM authority_progress_intent WHERE investigation_id=?", id.value());

        authority.transition(0L, InvestigationStatus.SCOPING, createdAt.plusSeconds(1));
        store.save(authority, 0L);

        assertThat(store.find(id).orElseThrow().version()).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM authority_progress_intent WHERE investigation_id=?", Integer.class,
                id.value())).isZero();
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
    void atomicallyCommitsTerminalSourceAndPublicationIntent() throws Exception {
        InvestigationAuthority authority = terminalAuthority();
        AuthorityId id = authority.snapshot().investigationId();
        InvestigationAuthority initial = newAuthority(id, authority.snapshot().incident());
        store.create(initial);

        var source = terminalCommitService.commit(authority, 0L);

        assertThat(source.sourceDigest()).matches("[0-9a-f]{64}");
        assertThat(terminalDao.findSource(id.value())).isPresent();
        assertThat(terminalDao.countPendingIntents(id.value())).isOne();
        var intent = terminalDao.findIntent(id.value()).orElseThrow();
        assertThat(intent.topicName()).isEqualTo("dpom.diagnosis-event.v2");
        assertThat(intent.schemaVersion()).isEqualTo("2.0");
        assertThat(intent.attemptCount()).isZero();
        assertThat(intent.canonicalSha256()).matches("[0-9a-f]{64}");
        assertThat(intent.canonicalContent()).contains("\"sourceAuthority\"")
                .contains("\"publicationIntentId\":\"" + intent.intentId() + "\"")
                .doesNotContain("password");
        assertThat(objectMapper.readTree(intent.canonicalContent()).path("investigationId").asText())
                .isEqualTo(id.value());
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
    void authorityOutboxSurvivesRetryAndWorkerRestartWithoutChangingFrozenContent() {
        InvestigationAuthority authority = terminalAuthority();
        AuthorityId id = authority.snapshot().investigationId();
        store.create(newAuthority(id, authority.snapshot().incident()));
        terminalCommitService.commit(authority, 0L);
        var original = terminalDao.findIntent(id.value()).orElseThrow();
        var policy = new DiagnosisDeliveryPolicy(3, Duration.ofHours(1), Duration.ofSeconds(1),
                Duration.ofSeconds(4), Duration.ofSeconds(10), 10);
        Clock firstClock = Clock.fixed(Instant.parse("2026-08-27T01:01:00Z"), java.time.ZoneOffset.UTC);
        var failing = new AuthorityPublicationDeliveryService(terminalDao, request -> {
            throw new IllegalStateException("broker unavailable");
        }, policy, firstClock, objectMapper, transactionManager, "KAFKA");

        failing.deliverReady("worker-before-restart");

        var pending = terminalDao.findIntent(id.value()).orElseThrow();
        assertThat(pending.status()).isEqualTo("PENDING");
        assertThat(pending.attemptCount()).isOne();
        assertThat(pending.canonicalContent()).isEqualTo(original.canonicalContent());
        Clock restartedClock = Clock.fixed(Instant.parse("2026-08-27T01:01:02Z"), java.time.ZoneOffset.UTC);
        var restarted = new AuthorityPublicationDeliveryService(terminalDao,
                request -> new DeliveryAcknowledgement(DeliveryOutcome.ACCEPTED, null),
                policy, restartedClock, objectMapper, transactionManager, "KAFKA");
        restarted.deliverReady("worker-after-restart");

        var delivered = terminalDao.findIntent(id.value()).orElseThrow();
        assertThat(delivered.status()).isEqualTo("DELIVERED");
        assertThat(delivered.attemptCount()).isEqualTo(2);
        assertThat(delivered.canonicalContent()).isEqualTo(original.canonicalContent());
        assertThat(delivered.canonicalSha256()).isEqualTo(original.canonicalSha256());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM authority_publication_attempt WHERE intent_id=?", Integer.class,
                delivered.intentId())).isEqualTo(2);
    }

    @Test
    void deadAuthorityOutboxCanBeReplayedWithoutRegeneratingCanonicalEvent() {
        InvestigationAuthority authority = terminalAuthority();
        AuthorityId id = authority.snapshot().investigationId();
        store.create(newAuthority(id, authority.snapshot().incident()));
        terminalCommitService.commit(authority, 0L);
        var original = terminalDao.findIntent(id.value()).orElseThrow();
        var policy = new DiagnosisDeliveryPolicy(1, Duration.ofHours(1), Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofSeconds(10), 10);
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T01:01:00Z"), java.time.ZoneOffset.UTC);
        var service = new AuthorityPublicationDeliveryService(terminalDao,
                request -> new DeliveryAcknowledgement(DeliveryOutcome.PERMANENT_REJECTION, "REMOTE_REJECTED"),
                policy, clock, objectMapper, transactionManager, "HTTP");
        service.deliverReady("worker-dead");
        assertThat(terminalDao.findIntent(id.value()).orElseThrow().status()).isEqualTo("DEAD");

        assertThat(service.replay(original.intentId())).isTrue();

        var replayed = terminalDao.findIntent(id.value()).orElseThrow();
        assertThat(replayed.status()).isEqualTo("PENDING");
        assertThat(replayed.attemptCount()).isZero();
        assertThat(replayed.canonicalContent()).isEqualTo(original.canonicalContent());
        assertThat(replayed.canonicalSha256()).isEqualTo(original.canonicalSha256());
    }

    @Test
    void progressOutboxRetriesAcrossRestartAndPreservesPerInvestigationOrder() throws Exception {
        InvestigationAuthority authority = newAuthority();
        AuthorityId id = authority.snapshot().investigationId();
        Instant createdAt = authority.snapshot().createdAt();
        store.create(authority);
        authority.transition(0L, InvestigationStatus.SCOPING, createdAt.plusSeconds(1));
        store.save(authority, 0L);
        String firstId = jdbcTemplate.queryForObject(
                "SELECT progress_id FROM authority_progress_intent "
                        + "WHERE investigation_id=? AND progress_sequence=1", String.class, id.value());
        var original = progressDao.findIntent(firstId).orElseThrow();
        var policy = new DiagnosisDeliveryPolicy(3, Duration.ofHours(1), Duration.ofSeconds(1),
                Duration.ofSeconds(4), Duration.ofSeconds(10), 10);
        Clock firstClock = Clock.fixed(Instant.parse("2026-08-27T01:01:00Z"), java.time.ZoneOffset.UTC);
        var failing = new AuthorityProgressDeliveryService(progressDao, request ->
                new DeliveryAcknowledgement(DeliveryOutcome.RETRYABLE_FAILURE, "BROKER_UNAVAILABLE"),
                policy, firstClock, transactionManager);

        failing.deliverReady("progress-before-restart");

        var pending = progressDao.findIntent(firstId).orElseThrow();
        assertThat(pending.status()).isEqualTo("PENDING");
        assertThat(pending.canonicalContent()).isEqualTo(original.canonicalContent());
        List<Long> deliveredSequences = new ArrayList<>();
        Clock restartedClock = Clock.fixed(Instant.parse("2026-08-27T01:01:02Z"), java.time.ZoneOffset.UTC);
        var restarted = new AuthorityProgressDeliveryService(progressDao, request -> {
            try {
                deliveredSequences.add(objectMapper.readTree(request.canonicalJson())
                        .path("progressSequence").asLong());
                return new DeliveryAcknowledgement(DeliveryOutcome.ACCEPTED, null);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }, policy, restartedClock, transactionManager);
        restarted.deliverReady("progress-after-restart");
        restarted.deliverReady("progress-after-restart");

        assertThat(deliveredSequences).containsExactly(1L, 2L);
        assertThat(progressDao.findIntent(firstId).orElseThrow().canonicalContent())
                .isEqualTo(original.canonicalContent());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM authority_progress_intent WHERE status='DELIVERED'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM authority_progress_attempt WHERE progress_id=?", Integer.class, firstId))
                .isEqualTo(2);
    }

    @Test
    void progressConflictFailsClosedAndRemainsSecretSafe() {
        InvestigationAuthority authority = newAuthority();
        store.create(authority);
        String progressId = jdbcTemplate.queryForObject(
                "SELECT progress_id FROM authority_progress_intent", String.class);
        var policy = new DiagnosisDeliveryPolicy(3, Duration.ofHours(1), Duration.ofSeconds(1),
                Duration.ofSeconds(4), Duration.ofSeconds(10), 10);
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T01:01:00Z"), java.time.ZoneOffset.UTC);
        var service = new AuthorityProgressDeliveryService(progressDao, request ->
                new DeliveryAcknowledgement(DeliveryOutcome.IDEMPOTENCY_CONFLICT, "unsafe provider detail"),
                policy, clock, transactionManager);

        service.deliverReady("progress-conflict");

        var dead = progressDao.findIntent(progressId).orElseThrow();
        assertThat(dead.status()).isEqualTo("DEAD");
        assertThat(dead.lastErrorCode()).isEqualTo("IDEMPOTENCY_CONFLICT");
        String attempt = jdbcTemplate.queryForObject(
                "SELECT CONCAT(outcome, '|', error_code) FROM authority_progress_attempt "
                        + "WHERE progress_id=?", String.class, progressId);
        assertThat(attempt).isEqualTo("IDEMPOTENCY_CONFLICT|IDEMPOTENCY_CONFLICT")
                .doesNotContain("provider");
    }

    @Test
    void uncertainProgressSendIsRecoveredAfterLeaseExpiryWithoutDuplicateAuthority() throws Exception {
        InvestigationAuthority authority = newAuthority();
        AuthorityId id = authority.snapshot().investigationId();
        store.create(authority);
        String progressId = jdbcTemplate.queryForObject(
                "SELECT progress_id FROM authority_progress_intent WHERE investigation_id=?", String.class,
                id.value());
        var policy = new DiagnosisDeliveryPolicy(3, Duration.ofHours(1), Duration.ofSeconds(1),
                Duration.ofSeconds(4), Duration.ofSeconds(1), 10);
        Clock firstClock = Clock.fixed(Instant.parse("2026-08-27T01:01:00Z"), java.time.ZoneOffset.UTC);
        var uncertain = new AuthorityProgressDeliveryService(progressDao, request -> {
            jdbcTemplate.update("UPDATE authority_progress_intent SET lease_expires_at=? WHERE progress_id=?",
                    LocalDateTime.ofInstant(firstClock.instant(), java.time.ZoneOffset.UTC), progressId);
            return new DeliveryAcknowledgement(DeliveryOutcome.ACCEPTED, null);
        }, policy, firstClock, transactionManager);

        uncertain.deliverReady("progress-uncertain-send");

        assertThat(progressDao.findIntent(progressId).orElseThrow().status()).isEqualTo("IN_FLIGHT");
        Clock restartedClock = Clock.fixed(Instant.parse("2026-08-27T01:01:02Z"), java.time.ZoneOffset.UTC);
        var restarted = new AuthorityProgressDeliveryService(progressDao,
                request -> new DeliveryAcknowledgement(DeliveryOutcome.EQUIVALENT_DUPLICATE, null),
                policy, restartedClock, transactionManager);
        restarted.deliverReady("progress-after-lease-expiry");

        var delivered = progressDao.findIntent(progressId).orElseThrow();
        assertThat(delivered.status()).isEqualTo("DELIVERED");
        assertThat(delivered.attemptCount()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM authority_progress_attempt WHERE progress_id=?", Integer.class,
                progressId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT outcome FROM authority_progress_attempt WHERE progress_id=?", String.class,
                progressId)).isEqualTo("EQUIVALENT_DUPLICATE");
    }

    @Test
    void exhaustedProgressRetryBlocksLaterSequenceUntilImmutableReplay() throws Exception {
        InvestigationAuthority authority = newAuthority();
        AuthorityId id = authority.snapshot().investigationId();
        Instant createdAt = authority.snapshot().createdAt();
        store.create(authority);
        authority.transition(0L, InvestigationStatus.SCOPING, createdAt.plusSeconds(1));
        store.save(authority, 0L);
        String firstId = jdbcTemplate.queryForObject(
                "SELECT progress_id FROM authority_progress_intent "
                        + "WHERE investigation_id=? AND progress_sequence=1", String.class, id.value());
        var original = progressDao.findIntent(firstId).orElseThrow();
        var policy = new DiagnosisDeliveryPolicy(1, Duration.ofHours(1), Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofSeconds(10), 10);
        Clock firstClock = Clock.fixed(Instant.parse("2026-08-27T01:01:00Z"), java.time.ZoneOffset.UTC);
        var exhausted = new AuthorityProgressDeliveryService(progressDao,
                request -> new DeliveryAcknowledgement(DeliveryOutcome.RETRYABLE_FAILURE, "BROKER_UNAVAILABLE"),
                policy, firstClock, transactionManager);

        exhausted.deliverReady("progress-exhausted");
        exhausted.deliverReady("progress-later-blocked");

        assertThat(progressDao.findIntent(firstId).orElseThrow().status()).isEqualTo("DEAD");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM authority_progress_intent WHERE status='DELIVERED'", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT outcome FROM authority_progress_attempt WHERE progress_id=?", String.class,
                firstId)).isEqualTo("RETRY_EXHAUSTED");
        assertThat(exhausted.replay(firstId)).isTrue();

        List<Long> deliveredSequences = new ArrayList<>();
        var recovered = new AuthorityProgressDeliveryService(progressDao, request -> {
            try {
                deliveredSequences.add(objectMapper.readTree(request.canonicalJson())
                        .path("progressSequence").asLong());
                return new DeliveryAcknowledgement(DeliveryOutcome.ACCEPTED, null);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }, policy, firstClock, transactionManager);
        recovered.deliverReady("progress-replay-first");
        recovered.deliverReady("progress-release-second");

        assertThat(deliveredSequences).containsExactly(1L, 2L);
        assertThat(progressDao.findIntent(firstId).orElseThrow().canonicalContent())
                .isEqualTo(original.canonicalContent());
        assertThat(progressDao.findIntent(firstId).orElseThrow().canonicalSha256())
                .isEqualTo(original.canonicalSha256());
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
