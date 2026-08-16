package com.dpom.agent.core.handoff;

import com.dpom.agent.common.handoff.EvidenceHandoffStore;
import com.dpom.agent.common.handoff.HandoffStoreException;
import com.dpom.agent.common.handoff.InMemoryEvidenceHandoffStore;
import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.EscalationDecisionCodec;
import com.dpom.agent.core.persistence.EscalationRow;
import com.dpom.agent.core.persistence.EvidenceBundleCodec;
import com.dpom.agent.core.persistence.EvidenceBundleDao;
import com.dpom.agent.core.persistence.EvidenceHandoffDao;
import com.dpom.agent.core.persistence.HypothesisDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.command.EscalationDecisionInsert;
import com.dpom.agent.core.persistence.command.HandoffImportInsert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 证据交接编排服务单元测试：升级、打包、审批与上传分离、审批绑定/过期/拒绝、OBS fail closed、幂等导入、审计 best-effort。
 */
class EvidenceHandoffServiceTest {

    private final InvestigationDao investigationDao = mock(InvestigationDao.class);
    private final IncidentDao incidentDao = mock(IncidentDao.class);
    private final ConclusionDao conclusionDao = mock(ConclusionDao.class);
    private final HypothesisDao hypothesisDao = mock(HypothesisDao.class);
    private final EvidenceBundleDao evidenceBundleDao = mock(EvidenceBundleDao.class);
    private final EvidenceHandoffDao handoffDao = mock(EvidenceHandoffDao.class);

    private static final long INV = 1L;

    @BeforeEach
    void setUp() {
        when(investigationDao.findById(INV)).thenReturn(Optional.of(investigation()));
        when(conclusionDao.findByInvestigationId(INV)).thenReturn(Optional.empty());
        when(hypothesisDao.findByInvestigationId(INV)).thenReturn(List.of());
        when(evidenceBundleDao.findBundleJson(INV)).thenReturn(Optional.empty());
    }

    @Test
    void escalationIsComputedPersistedAndAudited() {
        EvidenceHandoffService service = service(new InMemoryEvidenceHandoffStore(), HandoffConfig.defaults());
        EscalationDecision decision = service.escalate(INV);
        assertThat(decision.eligible()).isTrue();
        verify(handoffDao).insertEscalationDecision(any(EscalationDecisionInsert.class));
        verify(handoffDao).recordAudit(eq("ESCALATION"), eq("SUCCESS"), any(), any(), any(), any());
    }

    @Test
    void uploadWithoutApprovalIsRejected() {
        when(handoffDao.findUploadByPackageId("p1")).thenReturn(Optional.of(record("p1", UploadApprovalStatus.NOT_APPROVED)));
        EvidenceHandoffService service = service(new InMemoryEvidenceHandoffStore(), configuredConfig());
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> service.upload(INV, "p1"))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.NOT_APPROVED);
        verify(handoffDao, never()).markUploaded(anyLong(), anyString());
    }

    @Test
    void uploadRejectedAfterRejection() {
        when(handoffDao.findUploadByPackageId("p1")).thenReturn(Optional.of(record("p1", UploadApprovalStatus.REJECTED)));
        EvidenceHandoffService service = service(new InMemoryEvidenceHandoffStore(), configuredConfig());
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> service.upload(INV, "p1"))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.NOT_APPROVED);
    }

    @Test
    void approvalForOtherPackageDoesNotAuthorizeUpload() {
        when(handoffDao.findUploadByPackageId("p1")).thenReturn(Optional.of(record("p1", UploadApprovalStatus.APPROVED)));
        when(handoffDao.findUploadByPackageId("p2")).thenReturn(Optional.of(record("p2", UploadApprovalStatus.NOT_APPROVED)));
        EvidenceHandoffService service = service(new InMemoryEvidenceHandoffStore(), configuredConfig());
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> service.upload(INV, "p2"))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.NOT_APPROVED);
    }

    @Test
    void expiredApprovalIsRejected() {
        HandoffUpload expired = new HandoffUpload(7L, INV, "p1", null, 1, "abc", 100, UploadApprovalStatus.APPROVED,
                LocalDateTime.now().minusHours(2), "ref", "reason", LocalDateTime.now().minusHours(1), null, null);
        when(handoffDao.findUploadByPackageId("p1")).thenReturn(Optional.of(expired));
        EvidenceHandoffService service = service(new InMemoryEvidenceHandoffStore(), configuredConfig());
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> service.upload(INV, "p1"))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.APPROVAL_EXPIRED);
    }

    @Test
    void uploadFailsClosedWhenObsDisabled() {
        when(handoffDao.findUploadByPackageId("p1")).thenReturn(Optional.of(record("p1", UploadApprovalStatus.APPROVED)));
        EvidenceHandoffService service = service(new InMemoryEvidenceHandoffStore(), HandoffConfig.defaults());
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> service.upload(INV, "p1"))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.OBS_DISABLED);
        verify(handoffDao, never()).markUploaded(anyLong(), anyString());
    }

    @Test
    void uploadFailsClosedWhenAdapterUnavailable() {
        when(handoffDao.findUploadByPackageId("p1")).thenReturn(Optional.of(record("p1", UploadApprovalStatus.APPROVED)));
        EvidenceHandoffService service = service(disabledStore(), configuredConfig());
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> service.upload(INV, "p1"))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.OBS_ADAPTER_UNAVAILABLE);
        verify(handoffDao, never()).markUploaded(anyLong(), anyString());
    }

    @Test
    void uploadSucceedsWithApprovalAndFakeStore() {
        EvidenceHandoffService service = service(new InMemoryEvidenceHandoffStore(), configuredConfig());
        String packageId = buildPackage(service);
        when(handoffDao.findUploadByPackageId(packageId)).thenReturn(Optional.of(record(packageId, UploadApprovalStatus.APPROVED)));
        String objectKey = service.upload(INV, packageId);
        assertThat(objectKey).isEqualTo("prefix/" + packageId + ".zip");
        verify(handoffDao).markUploaded(anyLong(), eq(objectKey));
    }

    @Test
    void uploadFailureDoesNotMarkSuccess() {
        EvidenceHandoffStore failing = new EvidenceHandoffStore() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public void store(String objectKey, byte[] content) {
                throw new HandoffStoreException("NETWORK", "boom");
            }

            @Override
            public byte[] retrieve(String objectKey) {
                return new byte[0];
            }
        };
        EvidenceHandoffService service = service(failing, configuredConfig());
        String packageId = buildPackage(service);
        when(handoffDao.findUploadByPackageId(packageId)).thenReturn(Optional.of(record(packageId, UploadApprovalStatus.APPROVED)));
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> service.upload(INV, packageId))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.STORE_FAILURE);
        verify(handoffDao, never()).markUploaded(anyLong(), anyString());
        verify(handoffDao).recordAudit(eq("UPLOAD"), eq("FAILURE"), eq("STORE_FAILURE"), any(), any(), any());
    }

    @Test
    void approveBindsToPackageAndPersistsReference() {
        HandoffUpload notApproved = record("p1", UploadApprovalStatus.NOT_APPROVED);
        HandoffUpload approved = new HandoffUpload(7L, INV, "p1", null, 1, "abc", 100,
                UploadApprovalStatus.APPROVED, LocalDateTime.now(), "ticket-1", "approved by SRE",
                LocalDateTime.now().plusHours(1), null, null);
        when(handoffDao.findUploadByPackageId("p1")).thenReturn(Optional.of(notApproved), Optional.of(approved));
        EvidenceHandoffService service = service(new InMemoryEvidenceHandoffStore(), configuredConfig());
        ApprovalResult result = service.approveUpload(INV, "p1", "ticket-1", "approved by SRE");
        assertThat(result.status()).isEqualTo(UploadApprovalStatus.APPROVED);
        verify(handoffDao).approveUpload(anyLong(), eq("ticket-1"), eq("approved by SRE"), any(LocalDateTime.class));
    }

    @Test
    void approveRequiresReferenceAndReason() {
        when(handoffDao.findUploadByPackageId("p1")).thenReturn(Optional.of(record("p1", UploadApprovalStatus.NOT_APPROVED)));
        EvidenceHandoffService service = service(new InMemoryEvidenceHandoffStore(), configuredConfig());
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> service.approveUpload(INV, "p1", "", "reason"))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void importIsIdempotentOnDuplicateKeyWithMatchingIdentity() {
        InMemoryEvidenceHandoffStore store = new InMemoryEvidenceHandoffStore();
        store.store("prefix/p1.zip", new PackageSerializer().serialize(samplePackage()));
        doThrow(new DataIntegrityViolationException("dup")).when(handoffDao).insertImport(any(HandoffImportInsert.class));
        when(handoffDao.findImportByPackageId("p1"))
                .thenReturn(Optional.of(new HandoffImport(1L, "p1", "svc", "rel", "commit", null)));
        EvidenceHandoffService service = service(store, configuredConfig());
        ImportResult result = service.verifyAndImport("prefix/p1.zip", "svc", "rel", "commit");
        assertThat(result.alreadyImported()).isTrue();
        assertThat(result.bundle().service()).isEqualTo("svc");
    }

    @Test
    void nonUniqueIntegrityViolationIsNotIdempotent() {
        InMemoryEvidenceHandoffStore store = new InMemoryEvidenceHandoffStore();
        store.store("prefix/p1.zip", new PackageSerializer().serialize(samplePackage()));
        doThrow(new DataIntegrityViolationException("NOT NULL violation")).when(handoffDao)
                .insertImport(any(HandoffImportInsert.class));
        when(handoffDao.findImportByPackageId("p1")).thenReturn(Optional.empty());
        EvidenceHandoffService service = service(store, configuredConfig());
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> service.verifyAndImport("prefix/p1.zip", "svc", "rel", "commit"))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.PACKAGE_INVALID);
    }

    @Test
    void versionMismatchOnExistingImportFails() {
        InMemoryEvidenceHandoffStore store = new InMemoryEvidenceHandoffStore();
        store.store("prefix/p1.zip", new PackageSerializer().serialize(samplePackage()));
        doThrow(new DataIntegrityViolationException("dup")).when(handoffDao).insertImport(any(HandoffImportInsert.class));
        when(handoffDao.findImportByPackageId("p1"))
                .thenReturn(Optional.of(new HandoffImport(1L, "p1", "svc", "OTHER-rel", "commit", null)));
        EvidenceHandoffService service = service(store, configuredConfig());
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> service.verifyAndImport("prefix/p1.zip", "svc", "rel", "commit"))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.VERSION_MISMATCH);
    }

    @Test
    void auditWriteFailureDoesNotChangeBusinessResult() {
        doThrow(new RuntimeException("audit down")).when(handoffDao).recordAudit(anyString(), anyString(), any(),
                any(), any(), any());
        EvidenceHandoffService service = service(new InMemoryEvidenceHandoffStore(), HandoffConfig.defaults());
        EscalationDecision decision = service.escalate(INV);
        assertThat(decision.eligible()).isTrue();
    }

    private String buildPackage(EvidenceHandoffService service) {
        when(incidentDao.findById(anyLong())).thenReturn(Optional.of(incident()));
        when(evidenceBundleDao.findBundleJson(INV)).thenReturn(Optional.of(EvidenceBundleCodec.encode(bundle())));
        when(handoffDao.findEscalationRow(INV)).thenReturn(Optional.of(new EscalationRow(true,
                EscalationDecisionCodec.encodeReasons(List.of(EscalationReason.LOW_CONFIDENCE)),
                EscalationDecisionCodec.encodeMissing(List.of()), 10)));
        return service.buildPackage(INV).packageId();
    }

    private EvidenceHandoffService service(EvidenceHandoffStore store, HandoffConfig config) {
        return new EvidenceHandoffService(investigationDao, incidentDao, conclusionDao, hypothesisDao,
                evidenceBundleDao, handoffDao, new EscalationEvaluator(), new DiagnosticEvidencePackageBuilder(config),
                new PackageSerializer(), new PackageVerifier(), new DiagnosticEvidencePackageParser(), store, config);
    }

    private HandoffConfig configuredConfig() {
        return new HandoffConfig(HandoffProfile.DEVELOPMENT, 60, 1_048_576, 200, 1, true, "bucket", "prefix", 3600);
    }

    private Investigation investigation() {
        return new Investigation(INV, 100L, InvestigationStatus.INCONCLUSIVE, null, 30, 60, 1800, 5, null, null);
    }

    private Incident incident() {
        return new Incident(100L, "svc", "env", "rel", "commit", "symptom", null);
    }

    private EvidenceBundle bundle() {
        return new EvidenceBundle("svc", "env", "rel", "commit", "1h", List.of(), List.of(), List.of(),
                List.of(), List.of(), false);
    }

    private HandoffUpload record(String packageId, UploadApprovalStatus status) {
        return new HandoffUpload(7L, INV, packageId, null, 1, "abc", 100, status, null, null, null, null, null, null);
    }

    private DiagnosticEvidencePackage samplePackage() {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        sections.put("logs", List.of("template A count=1"));
        return new DiagnosticEvidencePackage(1, "p1", "svc", "env", "rel", "commit", "1h", sections, Map.of());
    }

    private EvidenceHandoffStore disabledStore() {
        return new EvidenceHandoffStore() {
            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public void store(String objectKey, byte[] content) {
                throw new HandoffStoreException("OBS_DISABLED", "disabled");
            }

            @Override
            public byte[] retrieve(String objectKey) {
                throw new HandoffStoreException("OBS_DISABLED", "disabled");
            }
        };
    }
}
