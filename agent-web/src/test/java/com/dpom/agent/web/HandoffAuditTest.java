package com.dpom.agent.web;

import com.dpom.agent.common.handoff.EvidenceHandoffStore;
import com.dpom.agent.common.handoff.InMemoryEvidenceHandoffStore;
import com.dpom.agent.core.handoff.BuiltPackage;
import com.dpom.agent.core.handoff.EvidenceHandoffService;
import com.dpom.agent.core.handoff.HandoffErrorCode;
import com.dpom.agent.core.handoff.HandoffException;
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
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * 追加式审计：升级/打包/审批/上传/校验/导入的成功与失败审计，不含证据正文或敏感值。
 */
@SpringBootTest(properties = {
        "dpom.handoff.mode=production",
        "dpom.handoff.obs.enabled=true",
        "dpom.handoff.obs.bucket=b",
        "dpom.handoff.obs.prefix=p"
})
class HandoffAuditTest {

    @Autowired
    private EvidenceHandoffService service;

    @Autowired
    private IncidentDao incidentDao;

    @Autowired
    private InvestigationDao investigationDao;

    @Autowired
    private EvidenceBundleDao evidenceBundleDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void recordsSuccessAuditForFullRoundTrip() {
        long invId = createInvestigation();
        service.escalate(invId);
        BuiltPackage pkg = service.buildPackage(invId);
        service.approveUpload(invId, pkg.packageId(), "ticket-1", "approved by SRE");
        String objectKey = service.upload(invId, pkg.packageId());
        service.verifyAndImport(objectKey, "asset-service", "1.0.0", "abc123");
        service.verifyAndImport(objectKey, "asset-service", "1.0.0", "abc123");

        assertThat(count("ESCALATION", "SUCCESS")).isEqualTo(1);
        assertThat(count("PACKAGE_BUILD", "SUCCESS")).isEqualTo(1);
        assertThat(count("APPROVAL", "SUCCESS")).isEqualTo(1);
        assertThat(count("UPLOAD", "SUCCESS")).isEqualTo(1);
        assertThat(count("VERIFY", "SUCCESS")).isEqualTo(2);
        assertThat(count("IMPORT", "SUCCESS")).isEqualTo(2);
    }

    @Test
    void recordsFailureAuditForUploadWithoutApproval() {
        long invId = createInvestigation();
        service.escalate(invId);
        BuiltPackage pkg = service.buildPackage(invId);
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> service.upload(invId, pkg.packageId()))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.NOT_APPROVED);
        assertThat(count("UPLOAD", "FAILURE")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM handoff_audit WHERE event_type = 'UPLOAD' AND error_code = 'NOT_APPROVED'",
                Integer.class)).isEqualTo(1);
    }

    private int count(String eventType, String result) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM handoff_audit WHERE event_type = ? AND result = ?",
                Integer.class, eventType, result);
    }

    private long createInvestigation() {
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
