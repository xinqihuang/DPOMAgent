package com.dpom.agent.web;

import com.dpom.agent.adapter.llm.FakeModelClient;
import com.dpom.agent.common.llm.ChatMessage;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.llm.ModelTurnResult;
import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.persistence.command.IncidentInsert;
import com.dpom.agent.core.persistence.command.EvidenceBundleInsert;
import com.dpom.agent.core.persistence.command.InvestigationInsert;
import com.dpom.agent.core.investigation.InvestigationCoordinator;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.investigation.SymptomBrain;
import com.dpom.agent.core.investigation.ToolExecutor;
import com.dpom.agent.core.logevidence.CodeEvidence;
import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.dpom.agent.core.logevidence.EvidenceProvenance;
import com.dpom.agent.core.logevidence.LogEvidence;
import com.dpom.agent.core.logevidence.LogTemplateSummary;
import com.dpom.agent.core.logevidence.ParameterDistribution;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.EvidenceBundleCodec;
import com.dpom.agent.core.persistence.EvidenceBundleDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * T106 调查循环结论护栏集成验收（FakeModelClient，默认离线）。
 */
@SpringBootTest
class LogEvidenceInvestigationGuardTest {

    @Autowired
    private IncidentDao incidentDao;

    @Autowired
    private InvestigationDao investigationDao;

    @Autowired
    private ConclusionDao conclusionDao;

    @Autowired
    private EvidenceBundleDao evidenceBundleDao;

    @Autowired
    private InvestigationCoordinator coordinator;

    private static LogEvidence log(String id) {
        LogTemplateSummary s = new LogTemplateSummary(1, "device <*> insert failed", 1, null, null,
                Map.of("ERROR", 1), List.of("device 1 insert failed"), new ParameterDistribution(Map.of()), false);
        return new LogEvidence(id, s, "svc", "prod", "1.0.0", "c", "1h", null, "drain3-0.9",
                new EvidenceProvenance("drain3", "c", null, null, "v1", null));
    }

    private static CodeEvidence source(String id) {
        return new CodeEvidence(id, "a", "AssetRepository.insert", "AssetRepository.java", 42, "c",
                "throw new IllegalStateException()", "VERIFIED");
    }

    private static EvidenceBundle bundle(List<LogEvidence> logs, List<CodeEvidence> codes) {
        return new EvidenceBundle("svc", "prod", "1.0.0", "c", "1h", logs, List.of(), codes, List.of(), List.of(), false);
    }

    private ModelClient concludingLlm(String evidenceIds) {
        return new FakeModelClient(request -> new ModelTurnResult(ChatMessage.assistant(
                "{\"type\":\"conclude\",\"resultType\":\"ROOT_CAUSE_FOUND\",\"rootCauseId\":\"AssetRepository.insert\",\"rootCause\":\"r\","
                        + "\"summary\":\"s\",\"evidenceIds\":\"" + evidenceIds + "\"}")));
    }

    private long createInvestigation() {
        IncidentInsert incidentCommand = new IncidentInsert("svc", "prod", "1.0.0", "c", "symptom");
        incidentDao.insert(incidentCommand);
        InvestigationInsert investigationCommand = new InvestigationInsert(incidentCommand.getId(),
                InvestigationStatus.CREATED, null, 30, 60, 1800, 5);
        investigationDao.insert(investigationCommand);
        return investigationCommand.getId();
    }

    /**
     * 有日志与 VERIFIED 源码证据时可形成根因。
     */
    @Test
    void rootCauseAllowedWithLogAndSource() {
        long id = createInvestigation();
        EvidenceBundleInsert bundleCommand = new EvidenceBundleInsert(id, "svc", "c",
                EvidenceBundleCodec.encode(bundle(List.of(log("ev-1")), List.of(source("code-1")))));
        evidenceBundleDao.insert(bundleCommand);
        coordinator.run(id, new SymptomBrain(concludingLlm("ev-1,code-1"), "symptom"), mock(ToolExecutor.class));

        Conclusion c = conclusionDao.findByInvestigationId(id).orElseThrow();
        assertThat(c.resultType()).isEqualTo("ROOT_CAUSE_FOUND");
        assertThat(investigationDao.findById(id).orElseThrow().status()).isEqualTo(InvestigationStatus.COMPLETED);
    }

    /**
     * 只有日志证据时拒绝 ROOT_CAUSE_FOUND，降级为 INCONCLUSIVE。
     */
    @Test
    void rootCauseDowngradedWhenOnlyLog() {
        long id = createInvestigation();
        EvidenceBundleInsert bundleCommand = new EvidenceBundleInsert(id, "svc", "c",
                EvidenceBundleCodec.encode(bundle(List.of(log("ev-1")), List.of())));
        evidenceBundleDao.insert(bundleCommand);
        coordinator.run(id, new SymptomBrain(concludingLlm("ev-1"), "symptom"), mock(ToolExecutor.class));

        assertThat(conclusionDao.findByInvestigationId(id).orElseThrow().resultType()).isEqualTo("INCONCLUSIVE");
        assertThat(investigationDao.findById(id).orElseThrow().status()).isEqualTo(InvestigationStatus.INCONCLUSIVE);
    }

    /**
     * 引用不存在的 evidenceId 时降级。
     */
    @Test
    void rootCauseDowngradedOnDanglingReference() {
        long id = createInvestigation();
        EvidenceBundleInsert bundleCommand = new EvidenceBundleInsert(id, "svc", "c",
                EvidenceBundleCodec.encode(bundle(List.of(log("ev-1")), List.of(source("code-1")))));
        evidenceBundleDao.insert(bundleCommand);
        coordinator.run(id, new SymptomBrain(concludingLlm("ev-1,code-999"), "symptom"), mock(ToolExecutor.class));

        assertThat(conclusionDao.findByInvestigationId(id).orElseThrow().resultType()).isEqualTo("INCONCLUSIVE");
        assertThat(investigationDao.findById(id).orElseThrow().status()).isEqualTo(InvestigationStatus.INCONCLUSIVE);
    }
}
