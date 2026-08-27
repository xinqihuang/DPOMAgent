package com.dpom.agent.web;

import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.handoff.HandoffImport;
import com.dpom.agent.core.handoff.HandoffUpload;
import com.dpom.agent.core.handoff.UploadApprovalStatus;
import com.dpom.agent.core.hypothesis.Hypothesis;
import com.dpom.agent.core.hypothesis.HypothesisStatus;
import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationRun;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.investigation.InvestigationStep;
import com.dpom.agent.core.observation.Observation;
import com.dpom.agent.core.persistence.ApiRequestRecord;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.DiagnosisEventAuditDao;
import com.dpom.agent.core.persistence.DiagnosisEventOutboxDao;
import com.dpom.agent.core.persistence.DiagnosisReplayNonceDao;
import com.dpom.agent.core.persistence.EscalationRow;
import com.dpom.agent.core.persistence.EvidenceBundleDao;
import com.dpom.agent.core.persistence.EvidenceHandoffDao;
import com.dpom.agent.core.persistence.HealthCheckMapper;
import com.dpom.agent.core.persistence.HypothesisDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationApiRequestDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.InvestigationRunDao;
import com.dpom.agent.core.persistence.InvestigationStepDao;
import com.dpom.agent.core.persistence.ObservationDao;
import com.dpom.agent.core.persistence.ScriptArtifactDao;
import com.dpom.agent.core.persistence.ToolCallAuditDao;
import com.dpom.agent.core.persistence.command.ApiRequestInsert;
import com.dpom.agent.core.persistence.command.ConclusionInsert;
import com.dpom.agent.core.persistence.command.DiagnosisEventAuditInsert;
import com.dpom.agent.core.persistence.command.DiagnosisEventLeaseCommand;
import com.dpom.agent.core.persistence.command.DiagnosisEventOutboxInsert;
import com.dpom.agent.core.persistence.command.DiagnosisEventTransitionCommand;
import com.dpom.agent.core.persistence.command.DiagnosisReplayNonceInsert;
import com.dpom.agent.core.persistence.command.EscalationDecisionInsert;
import com.dpom.agent.core.persistence.command.EvidenceBundleInsert;
import com.dpom.agent.core.persistence.command.HandoffImportInsert;
import com.dpom.agent.core.persistence.command.HandoffUploadInsert;
import com.dpom.agent.core.persistence.command.HypothesisInsert;
import com.dpom.agent.core.persistence.command.IncidentInsert;
import com.dpom.agent.core.persistence.command.InvestigationInsert;
import com.dpom.agent.core.persistence.command.InvestigationRunInsert;
import com.dpom.agent.core.persistence.command.InvestigationStepInsert;
import com.dpom.agent.core.persistence.command.ObservationInsert;
import com.dpom.agent.core.persistence.command.ScriptArtifactInsert;
import com.dpom.agent.core.persistence.command.ToolCallAuditInsert;
import com.dpom.agent.core.script.ScriptArtifact;
import com.dpom.agent.core.tool.ToolCallAudit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 外部真实 MySQL 8.0 Mapper 契约测试（无 Docker 依赖）。
 *
 * <p>通过环境变量 {@code DPOM_REAL_MYSQL_URL} 指向真实 MySQL（本地或 RDS），
 * 未设置时整类跳过；覆盖全部 13 个 Mapper 与核心持久化语义。</p>
 */
@EnabledIfEnvironmentVariable(named = "DPOM_REAL_MYSQL_URL", matches = ".+",
        disabledReason = "未设置 DPOM_REAL_MYSQL_URL，跳过外部真实 MySQL 契约测试")
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MybatisExternalMysqlContractTest {

    private static final Logger LOG = LoggerFactory.getLogger(MybatisExternalMysqlContractTest.class);

    static final String URL = System.getenv("DPOM_REAL_MYSQL_URL");
    static final String USER = System.getenv().getOrDefault("DPOM_REAL_MYSQL_USER", "root");
    static final String PASSWORD = System.getenv().getOrDefault("DPOM_REAL_MYSQL_PASSWORD", "");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired IncidentDao incidentDao;
    @Autowired InvestigationDao investigationDao;
    @Autowired ToolCallAuditDao toolCallAuditDao;
    @Autowired EvidenceBundleDao evidenceBundleDao;
    @Autowired InvestigationRunDao investigationRunDao;
    @Autowired InvestigationApiRequestDao apiRequestDao;
    @Autowired HypothesisDao hypothesisDao;
    @Autowired ConclusionDao conclusionDao;
    @Autowired ScriptArtifactDao scriptArtifactDao;
    @Autowired InvestigationStepDao stepDao;
    @Autowired ObservationDao observationDao;
    @Autowired EvidenceHandoffDao handoffDao;
    @Autowired HealthCheckMapper healthCheckMapper;
    @Autowired DiagnosisEventOutboxDao diagnosisOutboxDao;
    @Autowired DiagnosisEventAuditDao diagnosisAuditDao;
    @Autowired DiagnosisReplayNonceDao diagnosisNonceDao;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final List<String> TABLES = List.of(
            "diagnosis_event_audit", "diagnosis_event_replay_nonce", "diagnosis_event_outbox",
            "handoff_audit", "handoff_import", "handoff_upload", "escalation_decision",
            "investigation_api_request", "evidence_bundle", "observation", "investigation_step",
            "script_artifact", "conclusion", "hypothesis", "investigation_run",
            "tool_call_audit", "investigation", "incident");

    @AfterAll
    void cleanup() {
        for (String table : TABLES) {
            jdbcTemplate.execute("DELETE FROM " + table);
        }
    }

    @Test
    void healthCheckPing() {
        assertThat(healthCheckMapper.ping()).isEqualTo(1);
    }

    @Test
    void diagnosisEventMigrationAndLeaseSqlRunOnRealMysql8() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        String eventId = UUID.randomUUID().toString();
        DiagnosisEventOutboxInsert insert = new DiagnosisEventOutboxInsert(eventId, "real-" + eventId,
                900_001, 900_002, "investigation.completed", 1, "1.0", "{}", "a".repeat(64), now);
        assertThat(diagnosisOutboxDao.insert(insert)).isOne();
        String token = UUID.randomUUID().toString();
        assertThat(diagnosisOutboxDao.acquireLease(new DiagnosisEventLeaseCommand(
                insert.getId(), now, "real-mysql-test", token, now.plusMinutes(1)))).isOne();
        assertThat(diagnosisOutboxDao.markDelivered(new DiagnosisEventTransitionCommand(
                insert.getId(), token, now.plusSeconds(1), null, null))).isOne();
        diagnosisAuditDao.append(new DiagnosisEventAuditInsert(eventId, "investigation.completed",
                "ACKNOWLEDGED", "SUCCESS", null, null, null, "real-mysql"));
        diagnosisNonceDao.insert(new DiagnosisReplayNonceInsert("real-" + UUID.randomUUID(), now.plusMinutes(5)));
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM flyway_schema_history "
                + "WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1", String.class)).isEqualTo("12");
        LOG.info("REAL_EXECUTED diagnosis-event-outbox MySQL 8 contract");
    }

    @Test
    void incidentInsertAndSelectRoundTrip() {
        String svc = "svc-" + UUID.randomUUID();
        IncidentInsert command = new IncidentInsert(svc, "prod", "1.2.3", "abc123def456",
                "创建设备成功但数据库无记录");
        assertThat(incidentDao.insert(command)).isEqualTo(1);
        Long id = command.getId();
        assertThat(id).isPositive();

        Incident incident = incidentDao.findById(id).orElseThrow();
        assertThat(incident.serviceCode()).isEqualTo(svc);
        assertThat(incident.environment()).isEqualTo("prod");
        assertThat(incident.releaseVersion()).isEqualTo("1.2.3");
        assertThat(incident.commitSha()).isEqualTo("abc123def456");
        assertThat(incident.symptom()).isEqualTo("创建设备成功但数据库无记录");
        assertThat(incident.createdAt()).isNotNull();

        Incident byCode = incidentDao.findByServiceCodeAndEnvironment(svc, "prod").orElseThrow();
        assertThat(byCode.id()).isEqualTo(id);
    }

    @Test
    void investigationRoundTripAndConditionalUpdate() {
        long incidentId = createIncident();
        InvestigationInsert command = new InvestigationInsert(incidentId, InvestigationStatus.CREATED, null,
                50, 100, 1800, 5);
        investigationDao.insert(command);
        Long id = command.getId();
        assertThat(id).isPositive();

        Investigation investigation = investigationDao.findById(id).orElseThrow();
        assertThat(investigation.status()).isEqualTo(InvestigationStatus.CREATED);
        assertThat(investigation.currentRunId()).isNull();
        assertThat(investigation.maxSteps()).isEqualTo(50);
        assertThat(investigation.maxToolCalls()).isEqualTo(100);
        assertThat(investigation.maxDurationSeconds()).isEqualTo(1800);
        assertThat(investigation.maxNoProgressRounds()).isEqualTo(5);
        assertThat(investigation.createdAt()).isNotNull();
        assertThat(investigation.updatedAt()).isNotNull();

        investigationDao.updateCurrentRun(id, 42L);
        assertThat(investigationDao.findById(id).orElseThrow().currentRunId()).isEqualTo(42L);

        int updated = investigationDao.updateStatusIfActive(id, InvestigationStatus.RESEARCHING);
        assertThat(updated).isEqualTo(1);
        assertThat(investigationDao.findById(id).orElseThrow().status()).isEqualTo(InvestigationStatus.RESEARCHING);

        investigationDao.updateStatus(id, InvestigationStatus.COMPLETED);
        assertThat(investigationDao.findById(id).orElseThrow().status()).isEqualTo(InvestigationStatus.COMPLETED);
        int noop = investigationDao.updateStatusIfActive(id, InvestigationStatus.RESEARCHING);
        assertThat(noop).isEqualTo(0);
        assertThat(investigationDao.findById(id).orElseThrow().status()).isEqualTo(InvestigationStatus.COMPLETED);

        assertThat(investigationDao.findByIncidentId(incidentId)).anyMatch(i -> i.id().equals(id));
    }

    @Test
    void toolCallAuditAppendOnlyRoundTrip() {
        long investigationId = createInvestigation();
        ToolCallAuditInsert c1 = new ToolCallAuditInsert(investigationId, null, "codegraph",
                "{\"symbol\":\"DeviceRepository.updateStatus\"}", "ok", null, Boolean.TRUE, null);
        toolCallAuditDao.append(c1);
        assertThat(c1.getId()).isPositive();

        ToolCallAuditInsert c2 = new ToolCallAuditInsert(investigationId, 7L, "runtime",
                "{}", "err", 123L, Boolean.FALSE, "timeout");
        toolCallAuditDao.append(c2);
        assertThat(c2.getId()).isPositive();

        List<ToolCallAudit> audits = toolCallAuditDao.findByInvestigationId(investigationId);
        assertThat(audits).hasSizeGreaterThanOrEqualTo(2);
        ToolCallAudit a1 = audits.stream().filter(a -> a.id().equals(c1.getId())).findFirst().orElseThrow();
        assertThat(a1.runId()).isNull();
        assertThat(a1.durationMs()).isNull();
        assertThat(a1.success()).isTrue();
        assertThat(a1.createdAt()).isNotNull();
        ToolCallAudit a2 = audits.stream().filter(a -> a.id().equals(c2.getId())).findFirst().orElseThrow();
        assertThat(a2.runId()).isEqualTo(7L);
        assertThat(a2.durationMs()).isEqualTo(123L);
        assertThat(a2.success()).isFalse();
        assertThat(a2.errorMessage()).isEqualTo("timeout");
    }

    @Test
    void evidenceBundleJsonRoundTrip() {
        long investigationId = createInvestigation();
        String json = "{\"evidence\":[{\"evidenceId\":\"e-1\"}]}";
        EvidenceBundleInsert command = new EvidenceBundleInsert(investigationId, "asset-service", "sha-1", json);
        evidenceBundleDao.insert(command);
        assertThat(command.getId()).isPositive();
        assertThat(evidenceBundleDao.findBundleJson(investigationId)).contains(json);
    }

    @Test
    void investigationRunRoundTripAndFinish() {
        long investigationId = createInvestigation();
        InvestigationRunInsert command = new InvestigationRunInsert(investigationId, "m-1", "p-1", "t-1");
        investigationRunDao.insert(command);
        Long id = command.getId();
        assertThat(id).isPositive();

        InvestigationRun run = investigationRunDao.findById(id).orElseThrow();
        assertThat(run.modelVersion()).isEqualTo("m-1");
        assertThat(run.startedAt()).isNotNull();
        assertThat(run.endedAt()).isNull();

        investigationRunDao.finish(id, LocalDateTime.now().withNano(0));
        assertThat(investigationRunDao.findById(id).orElseThrow().endedAt()).isNotNull();
        assertThat(investigationRunDao.findByInvestigationId(investigationId)).anyMatch(r -> r.id().equals(id));
    }

    @Test
    void apiRequestIdempotencyAndLifecycle() {
        long investigationId = createInvestigation();
        String key = "idem-" + UUID.randomUUID();
        ApiRequestInsert command = new ApiRequestInsert(key, "hash-1", investigationId, "PENDING");
        apiRequestDao.insert(command);
        Long id = command.getId();
        assertThat(id).isPositive();

        ApiRequestRecord record = apiRequestDao.findByIdempotencyKey(key).orElseThrow();
        assertThat(record.id()).isEqualTo(id);
        assertThat(record.payloadHash()).isEqualTo("hash-1");
        assertThat(record.status()).isEqualTo("PENDING");

        apiRequestDao.updateRunning(id);
        assertThat(apiRequestDao.findByIdempotencyKey(key).orElseThrow().status()).isEqualTo("RUNNING");
        assertThat(apiRequestDao.findByIdempotencyKey(key).orElseThrow().startedAt()).isNotNull();

        apiRequestDao.updateDone(id, "DONE", "E_TIMEOUT");
        ApiRequestRecord done = apiRequestDao.findByIdempotencyKey(key).orElseThrow();
        assertThat(done.status()).isEqualTo("DONE");
        assertThat(done.completedAt()).isNotNull();
        assertThat(done.lastErrorCode()).isEqualTo("E_TIMEOUT");
    }

    @Test
    void hypothesisRoundTripAndStatusUpdate() {
        long investigationId = createInvestigation();
        HypothesisInsert command = new HypothesisInsert(investigationId, null, "根因是事务回滚",
                HypothesisStatus.PROPOSED, "缺提交日志");
        hypothesisDao.insert(command);
        Long id = command.getId();
        assertThat(id).isPositive();

        Hypothesis hypothesis = hypothesisDao.findById(id).orElseThrow();
        assertThat(hypothesis.parentId()).isNull();
        assertThat(hypothesis.description()).isEqualTo("根因是事务回滚");
        assertThat(hypothesis.status()).isEqualTo(HypothesisStatus.PROPOSED);
        assertThat(hypothesis.missingChecks()).isEqualTo("缺提交日志");
        assertThat(hypothesis.createdAt()).isNotNull();

        hypothesisDao.updateStatus(id, HypothesisStatus.VALIDATED);
        assertThat(hypothesisDao.findById(id).orElseThrow().status()).isEqualTo(HypothesisStatus.VALIDATED);
        assertThat(hypothesisDao.findByInvestigationId(investigationId)).anyMatch(h -> h.id().equals(id));
    }

    @Test
    void conclusionRoundTrip() {
        long investigationId = createInvestigation();
        ConclusionInsert command = new ConclusionInsert(investigationId, "ROOT_CAUSE", "rc-1",
                "更新条件不匹配导致未持久化", "e-1,e-2", "无", "事务回滚");
        conclusionDao.insert(command);
        Long id = command.getId();
        assertThat(id).isPositive();

        Conclusion conclusion = conclusionDao.findById(id).orElseThrow();
        assertThat(conclusion.resultType()).isEqualTo("ROOT_CAUSE");
        assertThat(conclusion.rootCauseId()).isEqualTo("rc-1");
        assertThat(conclusion.evidenceIds()).isEqualTo("e-1,e-2");
        assertThat(conclusionDao.findByInvestigationId(investigationId).orElseThrow().id()).isEqualTo(id);
    }

    @Test
    void scriptArtifactTypeAndReadOnlyRoundTrip() {
        long investigationId = createInvestigation();
        ScriptArtifactInsert command = new ScriptArtifactInsert(investigationId, "MITIGATION", "sql",
                "契约测试", "LOW", true, "PENDING", null, null, null, "SELECT 1", null, null, null,
                null, null, null);
        scriptArtifactDao.insert(command);
        Long id = command.getId();
        assertThat(id).isPositive();

        ScriptArtifact artifact = scriptArtifactDao.findById(id).orElseThrow();
        assertThat(artifact.type()).isEqualTo("MITIGATION");
        assertThat(artifact.language()).isEqualTo("sql");
        assertThat(artifact.readOnly()).isTrue();
        assertThat(artifact.createdAt()).isNotNull();
        assertThat(scriptArtifactDao.findByInvestigationId(investigationId)).anyMatch(a -> a.id().equals(id));
    }

    @Test
    void investigationStepAppendAndMaxOrder() {
        long investigationId = createInvestigation();
        InvestigationStepInsert s1 = new InvestigationStepInsert(investigationId, null, 1, "MINE", "第一步", "{}");
        stepDao.append(s1);
        InvestigationStepInsert s2 = new InvestigationStepInsert(investigationId, 9L, 2, "CODE", "第二步", "{}");
        stepDao.append(s2);
        assertThat(s1.getId()).isPositive();
        assertThat(s2.getId()).isPositive();

        List<InvestigationStep> steps = stepDao.findByInvestigationId(investigationId);
        assertThat(steps).anyMatch(s -> s.id().equals(s1.getId()) && s.runId() == null && s.stepOrder() == 1);
        assertThat(steps).anyMatch(s -> s.id().equals(s2.getId()) && s.runId() == 9L && s.stepOrder() == 2);
        assertThat(stepDao.maxStepOrder(investigationId)).isEqualTo(2);
    }

    @Test
    void observationRoundTrip() {
        long investigationId = createInvestigation();
        ObservationInsert command = new ObservationInsert(investigationId, null, "codegraph", null,
                "DeviceRepository.java", "h-1", null, "更新条件不匹配", "{}");
        observationDao.insert(command);
        Long id = command.getId();
        assertThat(id).isPositive();

        Observation observation = observationDao.findById(id).orElseThrow();
        assertThat(observation.runId()).isNull();
        assertThat(observation.source()).isEqualTo("codegraph");
        assertThat(observation.supportsHypothesisIds()).isEqualTo("h-1");
        assertThat(observation.summary()).isEqualTo("更新条件不匹配");
        assertThat(observationDao.findByInvestigationId(investigationId)).anyMatch(o -> o.id().equals(id));
    }

    @Test
    void evidenceHandoffEscalationAndUploadApprovalFlow() {
        long investigationId = createInvestigation();
        EscalationDecisionInsert esc = new EscalationDecisionInsert(investigationId, true, "高置信根因",
                "无", 85);
        handoffDao.insertEscalationDecision(esc);
        EscalationRow row = handoffDao.findEscalationRow(investigationId).orElseThrow();
        assertThat(row.eligible()).isTrue();
        assertThat(row.reasons()).isEqualTo("高置信根因");
        assertThat(row.confidence()).isEqualTo(85);

        String packageId = "pkg-" + UUID.randomUUID();
        HandoffUploadInsert up = new HandoffUploadInsert(investigationId, packageId, 1, "sum", 1024L);
        handoffDao.insertUpload(up);
        Long uploadId = up.getId();
        assertThat(uploadId).isPositive();

        HandoffUpload upload = handoffDao.findUploadByPackageId(packageId).orElseThrow();
        assertThat(upload.approvalStatus()).isEqualTo(UploadApprovalStatus.NOT_APPROVED);
        assertThat(upload.schemaVersion()).isEqualTo(1);
        assertThat(upload.sizeBytes()).isEqualTo(1024L);
        assertThat(upload.objectKey()).isNull();

        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1).withNano(0);
        int approved = handoffDao.approveUpload(uploadId, "approver-1", "同意", expiresAt);
        assertThat(approved).isEqualTo(1);
        HandoffUpload approvedUpload = handoffDao.findUploadByPackageId(packageId).orElseThrow();
        assertThat(approvedUpload.approvalStatus()).isEqualTo(UploadApprovalStatus.APPROVED);
        assertThat(approvedUpload.approverRef()).isEqualTo("approver-1");
        assertThat(approvedUpload.approvalReason()).isEqualTo("同意");
        assertThat(approvedUpload.approvalExpiresAt()).isEqualTo(expiresAt);
        assertThat(approvedUpload.approvedAt()).isNotNull();

        int uploaded = handoffDao.markUploaded(uploadId, "obs://bucket/key");
        assertThat(uploaded).isEqualTo(1);
        HandoffUpload uploadedUpload = handoffDao.findUploadByPackageId(packageId).orElseThrow();
        assertThat(uploadedUpload.objectKey()).isEqualTo("obs://bucket/key");
        assertThat(uploadedUpload.uploadedAt()).isNotNull();
        assertThat(handoffDao.findUploadByInvestigationId(investigationId)).anyMatch(u -> u.id().equals(uploadId));

        String packageId2 = "pkg-" + UUID.randomUUID();
        HandoffUploadInsert up2 = new HandoffUploadInsert(investigationId, packageId2, 1, "sum2", 8L);
        handoffDao.insertUpload(up2);
        int rejected = handoffDao.rejectUpload(up2.getId(), "approver-2", "驳回");
        assertThat(rejected).isEqualTo(1);
        assertThat(handoffDao.findUploadByPackageId(packageId2).orElseThrow().approvalStatus())
                .isEqualTo(UploadApprovalStatus.REJECTED);
    }

    @Test
    void evidenceHandoffImportUniqueAndAudit() throws Exception {
        long investigationId = createInvestigation();
        String packageId = "imp-" + UUID.randomUUID();
        HandoffImportInsert command = new HandoffImportInsert(packageId, "asset-service", "1.0.0", "sha-1");
        handoffDao.insertImport(command);
        assertThat(command.getId()).isPositive();

        HandoffImport imported = handoffDao.findImportByPackageId(packageId).orElseThrow();
        assertThat(imported.service()).isEqualTo("asset-service");
        assertThat(imported.release()).isEqualTo("1.0.0");
        assertThat(imported.commit()).isEqualTo("sha-1");
        assertThat(imported.createdAt()).isNotNull();

        HandoffImportInsert dup = new HandoffImportInsert(packageId, "asset-service", "1.0.0", "sha-1");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> handoffDao.insertImport(dup))
                .isInstanceOf(DuplicateKeyException.class);

        String concurrentPackage = "imp-" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    try {
                        handoffDao.insertImport(new HandoffImportInsert(concurrentPackage, "svc", "1.0", "sha"));
                        return true;
                    } catch (DuplicateKeyException ex) {
                        return false;
                    }
                }));
            }
            int success = 0;
            int dupCount = 0;
            for (Future<Boolean> f : results) {
                if (f.get()) success++; else dupCount++;
            }
            assertThat(success).isEqualTo(1);
            assertThat(dupCount).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        Integer before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM handoff_audit", Integer.class);
        handoffDao.recordAudit("APPROVE", "OK", null, investigationId, packageId, "corr-1");
        handoffDao.recordAudit("REJECT", "ERR", "E1", investigationId, packageId, "corr-2");
        Integer after = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM handoff_audit", Integer.class);
        assertThat(after).isEqualTo(before + 2);
    }

    private long createIncident() {
        IncidentInsert command = new IncidentInsert("svc-" + UUID.randomUUID(), "prod", "1.0.0", "abc123", "症状");
        incidentDao.insert(command);
        return command.getId();
    }

    private long createInvestigation() {
        long incidentId = createIncident();
        InvestigationInsert command = new InvestigationInsert(incidentId, InvestigationStatus.CREATED, null,
                50, 100, 1800, 5);
        investigationDao.insert(command);
        return command.getId();
    }
}
