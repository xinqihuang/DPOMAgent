package com.dpom.agent.web;

import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.logevidence.CodeAnchor;
import com.dpom.agent.core.logevidence.CodeEvidence;
import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.dpom.agent.core.logevidence.EvidenceProvenance;
import com.dpom.agent.core.logevidence.LogEvidence;
import com.dpom.agent.core.logevidence.LogTemplateSummary;
import com.dpom.agent.core.logevidence.ParameterDistribution;
import com.dpom.agent.core.persistence.EvidenceBundleCodec;
import com.dpom.agent.core.persistence.EvidenceBundleDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.command.EvidenceBundleInsert;
import com.dpom.agent.core.persistence.command.IncidentInsert;
import com.dpom.agent.core.persistence.command.InvestigationInsert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T107 证据束持久化与恢复验收（H2 隔离库，不依赖本机 MySQL）。
 */
@SpringBootTest
class EvidenceBundlePersistenceTest {

    @Autowired
    private IncidentDao incidentDao;

    @Autowired
    private InvestigationDao investigationDao;

    @Autowired
    private EvidenceBundleDao evidenceBundleDao;

    /**
     * 保存并恢复证据束，验证日志证据、代码证据、降级与来源可审计。
     */
    @Test
    void persistsAndRecoversBundle() {
        long investigationId = createInvestigation();

        LogTemplateSummary summary = new LogTemplateSummary(7, "device <*> insert failed", 3, null, null,
                Map.of("ERROR", 3), List.of("device 1 insert failed"),
                new ParameterDistribution(Map.of("deviceId", List.of("h:aaa"))), false);
        LogEvidence log = new LogEvidence("ev-1", summary, "asset-service", "prod", "1.0.0", "abc123", "1h", null,
                "drain3-0.9", new EvidenceProvenance("drain3", "abc123", null, null, "v1", null));
        CodeEvidence code = new CodeEvidence("code-1", "com.example.AssetRepository.insert", "AssetRepository.insert",
                "AssetRepository.java", 42, "abc123", "throw new IllegalStateException()", "VERIFIED");
        EvidenceBundle bundle = new EvidenceBundle("asset-service", "prod", "1.0.0", "abc123", "1h",
                List.of(log),
                List.of(new CodeAnchor("CLASS_METHOD", "com.example.AssetRepository.insert", "ev-1", 0.9, "v1")),
                List.of(code), List.of("LOG_MINER_UNAVAILABLE"), List.of(), false);

        EvidenceBundleInsert bundleCommand = new EvidenceBundleInsert(investigationId, bundle.service(),
                bundle.commit(), EvidenceBundleCodec.encode(bundle));
        evidenceBundleDao.insert(bundleCommand);
        long id = bundleCommand.getId();
        assertThat(id).isPositive();

        EvidenceBundle recovered = evidenceBundleDao.findBundleJson(investigationId)
                .map(EvidenceBundleCodec::decode).orElseThrow();
        assertThat(recovered.service()).isEqualTo("asset-service");
        assertThat(recovered.commit()).isEqualTo("abc123");
        assertThat(recovered.logEvidences()).hasSize(1);
        assertThat(recovered.logEvidences().get(0).summary().template()).isEqualTo("device <*> insert failed");
        assertThat(recovered.codeEvidences().get(0).status()).isEqualTo("VERIFIED");
        assertThat(recovered.degradations()).contains("LOG_MINER_UNAVAILABLE");
        assertThat(recovered.hasVerifiedSource()).isTrue();
    }

    /**
     * 创建调查。
     */
    private long createInvestigation() {
        IncidentInsert incidentCommand = new IncidentInsert("asset-service", "prod", "1.0.0", "abc123",
                "device create not persisted");
        incidentDao.insert(incidentCommand);
        InvestigationInsert investigationCommand = new InvestigationInsert(incidentCommand.getId(),
                InvestigationStatus.CREATED, null, 30, 60, 1800, 5);
        investigationDao.insert(investigationCommand);
        return investigationCommand.getId();
    }
}
