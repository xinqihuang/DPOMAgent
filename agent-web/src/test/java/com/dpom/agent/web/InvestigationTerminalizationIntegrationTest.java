package com.dpom.agent.web;

import com.dpom.agent.core.diagnosisevent.DiagnosisEventOutbox;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.investigation.InvestigationTerminalizationCommand;
import com.dpom.agent.core.investigation.InvestigationTerminalizationService;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.DiagnosisEventAuditDao;
import com.dpom.agent.core.persistence.DiagnosisEventOutboxDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.InvestigationRunDao;
import com.dpom.agent.core.persistence.command.IncidentInsert;
import com.dpom.agent.core.persistence.command.InvestigationInsert;
import com.dpom.agent.core.persistence.command.InvestigationRunInsert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * 调查终态事务的回滚、幂等和非评测终态集成测试。
 */
@SpringBootTest
class InvestigationTerminalizationIntegrationTest {

    @Autowired private IncidentDao incidentDao;
    @Autowired private InvestigationDao investigationDao;
    @Autowired private InvestigationRunDao runDao;
    @Autowired private ConclusionDao conclusionDao;
    @Autowired private DiagnosisEventOutboxDao outboxDao;
    @Autowired private InvestigationTerminalizationService service;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean private DiagnosisEventAuditDao auditDao;

    @AfterEach
    void clearSpyStubs() {
        reset(auditDao);
    }

    @Test
    void failedAndCancelledFinishDomainRecordsWithoutEvaluationEvents() {
        for (InvestigationStatus status : List.of(InvestigationStatus.FAILED, InvestigationStatus.CANCELLED)) {
            Fixture fixture = createFixture();
            service.terminalize(command(fixture, status, status.name()));

            assertThat(investigationDao.findById(fixture.investigationId()).orElseThrow().status()).isEqualTo(status);
            assertThat(conclusionDao.findByInvestigationId(fixture.investigationId())).isPresent();
            assertThat(runDao.findById(fixture.runId()).orElseThrow().endedAt()).isNotNull();
            assertThat(outboxDao.findByInvestigationId(fixture.investigationId())).isEmpty();
        }
    }

    @Test
    void canonicalizationFailureRollsBackEveryTerminalWrite() {
        Fixture fixture = createFixture();

        assertThatThrownBy(() -> service.terminalize(command(fixture, InvestigationStatus.COMPLETED,
                "x".repeat(17_000)))).hasMessage("PAYLOAD_TOO_LARGE");

        assertRolledBack(fixture);
    }

    @Test
    void auditFailureRollsBackEveryTerminalWrite() {
        Fixture fixture = createFixture();
        doThrow(new DataIntegrityViolationException("forced audit failure")).when(auditDao).append(any());

        assertThatThrownBy(() -> service.terminalize(command(fixture, InvestigationStatus.COMPLETED, "summary")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertRolledBack(fixture);
    }

    @Test
    void racingAndRepeatedTerminalizationCreatesOneImmutableEvent() throws Exception {
        Fixture fixture = createFixture();
        InvestigationTerminalizationCommand command = command(fixture, InvestigationStatus.COMPLETED, "summary");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> runAfter(start, command));
            Future<?> second = pool.submit(() -> runAfter(start, command));
            start.countDown();
            first.get();
            second.get();
        } finally {
            pool.shutdownNow();
        }

        DiagnosisEventOutbox event = outboxDao.findByInvestigationId(fixture.investigationId()).getFirst();
        String originalContent = event.canonicalContent();
        service.terminalize(command);
        assertThat(outboxDao.findByInvestigationId(fixture.investigationId()))
                .singleElement().satisfies(stored -> assertThat(stored.canonicalContent()).isEqualTo(originalContent));
        assertThat(count("conclusion", fixture.investigationId())).isOne();
        assertThat(count("diagnosis_event_outbox", fixture.investigationId())).isOne();
    }

    private void runAfter(CountDownLatch start, InvestigationTerminalizationCommand command) {
        try {
            start.await();
            service.terminalize(command);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void assertRolledBack(Fixture fixture) {
        assertThat(investigationDao.findById(fixture.investigationId()).orElseThrow().status())
                .isEqualTo(InvestigationStatus.SCOPING);
        assertThat(conclusionDao.findByInvestigationId(fixture.investigationId())).isEmpty();
        assertThat(runDao.findById(fixture.runId()).orElseThrow().endedAt()).isNull();
        assertThat(outboxDao.findByInvestigationId(fixture.investigationId())).isEmpty();
    }

    private int count(String table, long investigationId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE investigation_id = ?",
                Integer.class, investigationId);
    }

    private InvestigationTerminalizationCommand command(Fixture fixture, InvestigationStatus status, String summary) {
        return new InvestigationTerminalizationCommand(fixture.investigationId(), fixture.runId(), status,
                status == InvestigationStatus.COMPLETED ? "ROOT_CAUSE_FOUND" : status.name(),
                null, null, summary, null);
    }

    private Fixture createFixture() {
        IncidentInsert incident = new IncidentInsert("asset-service", "prod", "1.0.0", "abcdef1", "symptom");
        incidentDao.insert(incident);
        InvestigationInsert investigation = new InvestigationInsert(incident.getId(), InvestigationStatus.SCOPING,
                null, 30, 60, 1800, 5);
        investigationDao.insert(investigation);
        InvestigationRunInsert run = new InvestigationRunInsert(investigation.getId(), null, null, null);
        runDao.insert(run);
        investigationDao.updateCurrentRun(investigation.getId(), run.getId());
        return new Fixture(investigation.getId(), run.getId());
    }

    private record Fixture(long investigationId, long runId) {
    }
}
