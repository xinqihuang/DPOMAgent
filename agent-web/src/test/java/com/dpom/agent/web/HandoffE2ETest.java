package com.dpom.agent.web;

import com.dpom.agent.common.handoff.EvidenceHandoffStore;
import com.dpom.agent.common.handoff.InMemoryEvidenceHandoffStore;
import com.dpom.agent.core.handoff.ApprovalResult;
import com.dpom.agent.core.handoff.BuiltPackage;
import com.dpom.agent.core.handoff.EscalationDecision;
import com.dpom.agent.core.handoff.EvidenceHandoffService;
import com.dpom.agent.core.handoff.ImportResult;
import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.dpom.agent.core.persistence.EvidenceBundleDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * fake-store 端到端验收：升级 → 打包 → 独立审批 → 审批后上传 → 下载校验 → 恢复 → 幂等。
 * fake store 通过测试专用配置显式装配（@TestConfiguration @Primary），不依赖正式配置开关。
 */
@SpringBootTest(properties = {
        "dpom.handoff.mode=production",
        "dpom.handoff.obs.enabled=true",
        "dpom.handoff.obs.bucket=dpom-evidence",
        "dpom.handoff.obs.prefix=handoff"
})
class HandoffE2ETest {

    @Autowired
    private EvidenceHandoffService service;

    @Autowired
    private IncidentDao incidentDao;

    @Autowired
    private InvestigationDao investigationDao;

    @Autowired
    private EvidenceBundleDao evidenceBundleDao;

    @Test
    void fullHandoffRoundTripIsIdempotent() {
        long investigationId = createInconclusiveInvestigation();

        EscalationDecision decision = service.escalate(investigationId);
        assertThat(decision.eligible()).isTrue();

        BuiltPackage pkg = service.buildPackage(investigationId);
        assertThat(pkg.packageId()).isNotBlank();
        assertThat(pkg.checksum()).hasSize(64);

        ApprovalResult approval = service.approveUpload(investigationId, pkg.packageId(), "ticket-1",
                "approved by SRE");
        assertThat(approval.status().name()).isEqualTo("APPROVED");

        String objectKey = service.upload(investigationId, pkg.packageId());
        assertThat(objectKey).startsWith("handoff/");

        ImportResult first = service.verifyAndImport(objectKey, "asset-service", "1.0.0", "abc123");
        assertThat(first.alreadyImported()).isFalse();
        assertThat(first.bundle().service()).isEqualTo("asset-service");
        assertThat(first.bundle().release()).isEqualTo("1.0.0");
        assertThat(first.bundle().commit()).isEqualTo("abc123");

        ImportResult again = service.verifyAndImport(objectKey, "asset-service", "1.0.0", "abc123");
        assertThat(again.alreadyImported()).isTrue();
    }

    private long createInconclusiveInvestigation() {
        long incidentId = incidentDao.insert(
                new Incident(null, "asset-service", "prod", "1.0.0", "abc123", "device create not persisted", null));
        long investigationId = investigationDao.insert(new Investigation(null, incidentId, InvestigationStatus.INCONCLUSIVE,
                null, 30, 60, 1800, 5, null, null));
        evidenceBundleDao.save(investigationId, new EvidenceBundle("asset-service", "prod", "1.0.0", "abc123", "1h",
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                false));
        return investigationId;
    }

    @TestConfiguration
    static class FakeStoreConfig {
        @Bean
        @Primary
        EvidenceHandoffStore fakeStore() {
            return new InMemoryEvidenceHandoffStore();
        }
    }
}
