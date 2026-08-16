package com.dpom.agent.web;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.common.logtemplate.LogParseResult;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.logevidence.CodeEvidence;
import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.dpom.agent.core.logevidence.EvidenceBundleBuilder;
import com.dpom.agent.core.logevidence.EvidenceProvenance;
import com.dpom.agent.core.logevidence.EvidenceTimeline;
import com.dpom.agent.core.logevidence.EvidenceTimelineService;
import com.dpom.agent.core.logevidence.LogEvidence;
import com.dpom.agent.core.logevidence.LogEvidenceService;
import com.dpom.agent.core.logevidence.LogTemplateSummary;
import com.dpom.agent.core.logevidence.ParameterDistribution;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.EvidenceBundleCodec;
import com.dpom.agent.core.persistence.EvidenceBundleDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.command.ConclusionInsert;
import com.dpom.agent.core.persistence.command.EvidenceBundleInsert;
import com.dpom.agent.core.persistence.command.IncidentInsert;
import com.dpom.agent.core.persistence.command.InvestigationInsert;
import com.dpom.agent.core.workspace.CodeWorkspace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T107 证据持久化 + Timeline 审计集成验收（H2 隔离库）。
 */
@SpringBootTest
class EvidenceTimelinePersistenceTest {

    @Autowired
    private EvidenceBundleDao bundleDao;

    @Autowired
    private ConclusionDao conclusionDao;

    @Autowired
    private EvidenceTimelineService timelineService;

    @Autowired
    private IncidentDao incidentDao;

    @Autowired
    private InvestigationDao investigationDao;

    /**
     * 保存 → 重新读取 → timeline 查询 → 字段与引用一致。
     */
    @Test
    void timelineRoundTripPreservesFieldsAndReferences() {
        long id = createInvestigation();
        LogTemplateSummary summary = new LogTemplateSummary(7, "device <*> insert failed", 3, null, null,
                Map.of("ERROR", 3), List.of("device 1 insert failed"),
                new ParameterDistribution(Map.of("deviceId", List.of("h:aaa"))), false);
        LogEvidence log = new LogEvidence("ev-1", summary, "svc", "prod", "1.0.0", "abc123", "1h", null, "drain3-0.9",
                new EvidenceProvenance("drain3", "abc123", null, null, "v1", null));
        CodeEvidence code = new CodeEvidence("code-1", "a", "AssetRepository.insert", "AssetRepository.java", 42,
                "abc123", "throw new IllegalStateException()", "VERIFIED");
        EvidenceBundle bundle = new EvidenceBundle("svc", "prod", "1.0.0", "abc123", "1h", List.of(log), List.of(),
                List.of(code), List.of("LOG_MINER_UNAVAILABLE"), List.of(), false);
        EvidenceBundleInsert bundleCommand = new EvidenceBundleInsert(id, bundle.service(), bundle.commit(),
                EvidenceBundleCodec.encode(bundle));
        bundleDao.insert(bundleCommand);
        ConclusionInsert conclusionCommand = new ConclusionInsert(id, "ROOT_CAUSE_FOUND", "AssetRepository.insert",
                "r", "ev-1,code-1", null, "s");
        conclusionDao.insert(conclusionCommand);

        EvidenceTimeline timeline = timelineService.timeline(id);

        assertThat(timeline.release()).isEqualTo("1.0.0");
        assertThat(timeline.entries()).hasSize(2);
        assertThat(timeline.entries()).anySatisfy(e -> {
            assertThat(e.evidenceId()).isEqualTo("ev-1");
            assertThat(e.type()).isEqualTo("LOG");
            assertThat(e.provenanceSource()).isEqualTo("drain3");
            assertThat(e.commit()).isEqualTo("abc123");
            assertThat(e.ruleVersion()).isEqualTo("v1");
            assertThat(e.minerVersion()).isEqualTo("drain3-0.9");
            assertThat(e.truncated()).isFalse();
        });
        assertThat(timeline.entries()).anySatisfy(e -> {
            assertThat(e.evidenceId()).isEqualTo("code-1");
            assertThat(e.type()).isEqualTo("SOURCE");
            assertThat(e.commit()).isEqualTo("abc123");
            assertThat(e.degradation()).isNull();
        });
        assertThat(timeline.degradations()).contains("LOG_MINER_UNAVAILABLE");
        assertThat(timeline.conclusionResultType()).isEqualTo("ROOT_CAUSE_FOUND");
        assertThat(timeline.conclusionEvidenceIds()).contains("ev-1").contains("code-1");
    }

    /**
     * 数据库中不出现原始 secret（管道先脱敏再持久化）。
     */
    @Test
    void noRawSecretPersisted() {
        long id = createInvestigation();
        LogTemplateMinerClient miner = mock(LogTemplateMinerClient.class);
        when(miner.parseLogs(any())).thenReturn(List.of(new LogParseResult(1, 1, "password=<*>", List.of())));
        CodeGraphClient cgc = mock(CodeGraphClient.class);
        when(cgc.findSymbol(anyString(), anyString())).thenReturn(List.of());
        LogEvidenceService service = new LogEvidenceService(miner, cgc, mock(CodeWorkspace.class),
                new EvidenceBundleBuilder(100_000));
        CodeSnapshot snapshot = new CodeSnapshot("s1", "svc", "c", "/x", SnapshotStatus.READY);
        EvidenceBundle bundle = service.run("svc", "prod", "1.0.0", "c", "1h", "drain3-0.9", snapshot,
                List.of("ERROR svc - password=secret123 device 1 insert failed"));
        EvidenceBundleInsert bundleCommand = new EvidenceBundleInsert(id, bundle.service(), bundle.commit(),
                EvidenceBundleCodec.encode(bundle));
        bundleDao.insert(bundleCommand);

        EvidenceBundle recovered = bundleDao.findBundleJson(id).map(EvidenceBundleCodec::decode).orElseThrow();
        String sample = recovered.logEvidences().get(0).summary().representativeSamples().get(0);
        assertThat(sample).doesNotContain("secret123");
        assertThat(sample).contains("h:");
    }

    private long createInvestigation() {
        IncidentInsert incidentCommand = new IncidentInsert("svc", "prod", "1.0.0", "c", "symptom");
        incidentDao.insert(incidentCommand);
        InvestigationInsert investigationCommand = new InvestigationInsert(incidentCommand.getId(),
                InvestigationStatus.CREATED, null, 30, 60, 1800, 5);
        investigationDao.insert(investigationCommand);
        return investigationCommand.getId();
    }
}
