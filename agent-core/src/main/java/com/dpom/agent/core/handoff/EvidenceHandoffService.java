package com.dpom.agent.core.handoff;

import com.dpom.agent.common.handoff.EvidenceHandoffStore;
import com.dpom.agent.common.handoff.HandoffStoreException;
import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.hypothesis.Hypothesis;
import com.dpom.agent.core.hypothesis.HypothesisStatus;
import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.EvidenceBundleDao;
import com.dpom.agent.core.persistence.EvidenceHandoffDao;
import com.dpom.agent.core.persistence.EscalationDecisionCodec;
import com.dpom.agent.core.persistence.EvidenceBundleCodec;
import com.dpom.agent.core.persistence.HypothesisDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.command.EscalationDecisionInsert;
import com.dpom.agent.core.persistence.command.HandoffImportInsert;
import com.dpom.agent.core.persistence.command.HandoffUploadInsert;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 证据交接编排：升级判定 → 打包 → 独立审批 → 审批后上传 → 研发侧校验/恢复。审批与上传分离，全程追加审计。
 */
public class EvidenceHandoffService {

    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILURE = "FAILURE";

    private final InvestigationDao investigationDao;
    private final IncidentDao incidentDao;
    private final ConclusionDao conclusionDao;
    private final HypothesisDao hypothesisDao;
    private final EvidenceBundleDao evidenceBundleDao;
    private final EvidenceHandoffDao handoffDao;
    private final EscalationEvaluator evaluator;
    private final DiagnosticEvidencePackageBuilder builder;
    private final PackageSerializer serializer;
    private final PackageVerifier verifier;
    private final DiagnosticEvidencePackageParser parser;
    private final EvidenceHandoffStore store;
    private final HandoffConfig config;
    private final ConcurrentMap<String, byte[]> packageCache = new ConcurrentHashMap<>();

    /**
     * 构造器注入。
     */
    public EvidenceHandoffService(InvestigationDao investigationDao, IncidentDao incidentDao,
                                  ConclusionDao conclusionDao, HypothesisDao hypothesisDao,
                                  EvidenceBundleDao evidenceBundleDao, EvidenceHandoffDao handoffDao,
                                  EscalationEvaluator evaluator, DiagnosticEvidencePackageBuilder builder,
                                  PackageSerializer serializer, PackageVerifier verifier,
                                  DiagnosticEvidencePackageParser parser, EvidenceHandoffStore store,
                                  HandoffConfig config) {
        this.investigationDao = investigationDao;
        this.incidentDao = incidentDao;
        this.conclusionDao = conclusionDao;
        this.hypothesisDao = hypothesisDao;
        this.evidenceBundleDao = evidenceBundleDao;
        this.handoffDao = handoffDao;
        this.evaluator = evaluator;
        this.builder = builder;
        this.serializer = serializer;
        this.verifier = verifier;
        this.parser = parser;
        this.store = store;
        this.config = config;
    }

    /**
     * 计算并持久化升级判定。
     *
     * @param investigationId 调查 id
     * @return 升级判定
     */
    public EscalationDecision escalate(long investigationId) {
        try {
            requireInvestigation(investigationId);
            EscalationDecision decision = evaluator.evaluate(buildContext(investigationId), config);
            EscalationDecisionInsert escalationCmd = new EscalationDecisionInsert(investigationId,
                    decision.eligible(), EscalationDecisionCodec.encodeReasons(decision.reasons()),
                    EscalationDecisionCodec.encodeMissing(decision.missingEvidence()), decision.confidence());
            handoffDao.insertEscalationDecision(escalationCmd);
            audit("ESCALATION", SUCCESS, null, investigationId, null);
            return decision;
        } catch (HandoffException e) {
            audit("ESCALATION", FAILURE, e.code().name(), investigationId, null);
            throw e;
        } catch (RuntimeException e) {
            audit("ESCALATION", FAILURE, "INTERNAL_ERROR", investigationId, null);
            throw e;
        }
    }

    /**
     * 构建证据包（要求升级已判定且 eligible）。
     *
     * @param investigationId 调查 id
     * @return 构建完成的证据包
     */
    public BuiltPackage buildPackage(long investigationId) {
        try {
            Investigation inv = requireInvestigation(investigationId);
            Incident incident = incidentDao.findById(inv.incidentId())
                    .orElseThrow(() -> new HandoffException(HandoffErrorCode.PACKAGE_INVALID, "incident not found"));
            EscalationDecision decision = handoffDao.findEscalationRow(investigationId)
                    .map(row -> new EscalationDecision(row.eligible(),
                            EscalationDecisionCodec.decodeReasons(row.reasons()),
                            EscalationDecisionCodec.decodeMissing(row.missingEvidence()), row.confidence()))
                    .orElseGet(() -> evaluator.evaluate(buildContext(investigationId), config));
            if (!decision.eligible()) {
                throw new HandoffException(HandoffErrorCode.NOT_ELIGIBLE, "investigation not eligible for handoff");
            }
            String packageId = UUID.randomUUID().toString();
            DiagnosticEvidencePackage pkg = builder.build(packageId, incident.serviceCode(), incident.environment(),
                    incident.releaseVersion(), incident.commitSha(), timeRange(investigationId),
                    buildSections(investigationId));
            byte[] zip = serializer.serialize(pkg);
            String checksum = PackageSerializer.sha256(zip);
            HandoffUploadInsert uploadCmd = new HandoffUploadInsert(investigationId, packageId,
                    pkg.schemaVersion(), checksum, zip.length);
            handoffDao.insertUpload(uploadCmd);
            packageCache.put(packageId, zip);
            audit("PACKAGE_BUILD", SUCCESS, null, investigationId, packageId);
            return new BuiltPackage(packageId, checksum, zip.length, zip);
        } catch (HandoffException e) {
            audit("PACKAGE_BUILD", FAILURE, e.code().name(), investigationId, null);
            throw e;
        } catch (RuntimeException e) {
            audit("PACKAGE_BUILD", FAILURE, "INTERNAL_ERROR", investigationId, null);
            throw e;
        }
    }

    /**
     * 批准上传：独立、持久化、绑定具体 packageId 的审批决定。
     *
     * @param investigationId 调查 id
     * @param packageId       包标识
     * @param approverRef     外部审批引用（必填）
     * @param reason          审批理由（必填）
     * @return 审批结果
     */
    public ApprovalResult approveUpload(long investigationId, String packageId, String approverRef, String reason) {
        try {
            HandoffUpload record = requirePackage(investigationId, packageId);
            requireText(approverRef, "approverRef");
            requireText(reason, "reason");
            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(config.approvalTtlSeconds());
            handoffDao.approveUpload(record.id(), approverRef, reason, expiresAt);
            audit("APPROVAL", SUCCESS, null, investigationId, packageId);
            HandoffUpload updated = handoffDao.findUploadByPackageId(packageId).orElse(record);
            return new ApprovalResult(packageId, updated.approvalStatus(), updated.approvedAt());
        } catch (HandoffException e) {
            audit("APPROVAL", FAILURE, e.code().name(), investigationId, packageId);
            throw e;
        } catch (RuntimeException e) {
            audit("APPROVAL", FAILURE, "INTERNAL_ERROR", investigationId, packageId);
            throw e;
        }
    }

    /**
     * 拒绝上传：独立、持久化、绑定具体 packageId 的审批决定。
     *
     * @param investigationId 调查 id
     * @param packageId       包标识
     * @param approverRef     外部审批引用（必填）
     * @param reason          拒绝理由（必填）
     * @return 审批结果
     */
    public ApprovalResult rejectUpload(long investigationId, String packageId, String approverRef, String reason) {
        try {
            HandoffUpload record = requirePackage(investigationId, packageId);
            requireText(approverRef, "approverRef");
            requireText(reason, "reason");
            handoffDao.rejectUpload(record.id(), approverRef, reason);
            audit("REJECTION", SUCCESS, null, investigationId, packageId);
            HandoffUpload updated = handoffDao.findUploadByPackageId(packageId).orElse(record);
            return new ApprovalResult(packageId, updated.approvalStatus(), updated.approvedAt());
        } catch (HandoffException e) {
            audit("REJECTION", FAILURE, e.code().name(), investigationId, packageId);
            throw e;
        } catch (RuntimeException e) {
            audit("REJECTION", FAILURE, "INTERNAL_ERROR", investigationId, packageId);
            throw e;
        }
    }

    /**
     * 上传（只读取数据库中已有的 APPROVED 状态，不接受调用方 approval 入参）。
     *
     * @param investigationId 调查 id
     * @param packageId       包标识
     * @return 对象名
     */
    public String upload(long investigationId, String packageId) {
        try {
            HandoffUpload record = requirePackage(investigationId, packageId);
            if (record.approvalStatus() != UploadApprovalStatus.APPROVED) {
                throw new HandoffException(HandoffErrorCode.NOT_APPROVED, "package not approved");
            }
            if (record.approvalExpiresAt() != null && record.approvalExpiresAt().isBefore(LocalDateTime.now())) {
                throw new HandoffException(HandoffErrorCode.APPROVAL_EXPIRED, "approval expired");
            }
            requireObsTransport();
            byte[] bytes = packageCache.get(packageId);
            if (bytes == null) {
                throw new HandoffException(HandoffErrorCode.PACKAGE_INVALID, "package bytes unavailable");
            }
            String objectKey = config.allowedPrefix() + "/" + packageId + ".zip";
            try {
                store.store(objectKey, bytes);
            } catch (HandoffStoreException e) {
                throw new HandoffException(HandoffErrorCode.STORE_FAILURE, "upload failed: " + e.code());
            }
            handoffDao.markUploaded(record.id(), objectKey);
            audit("UPLOAD", SUCCESS, null, investigationId, packageId);
            return objectKey;
        } catch (HandoffException e) {
            audit("UPLOAD", FAILURE, e.code().name(), investigationId, packageId);
            throw e;
        } catch (RuntimeException e) {
            audit("UPLOAD", FAILURE, "INTERNAL_ERROR", investigationId, packageId);
            throw e;
        }
    }

    /**
     * 研发侧下载、校验并恢复；重复导入幂等（数据库唯一键为最终仲裁）。
     *
     * @param objectKey       对象名
     * @param expectedService 期望服务编码
     * @param expectedRelease 期望发布版本
     * @param expectedCommit  期望提交 SHA
     * @return 导入结果
     */
    public ImportResult verifyAndImport(String objectKey, String expectedService, String expectedRelease,
                                        String expectedCommit) {
        RecoveredEvidencePackage pkg = verify(objectKey, expectedService, expectedRelease, expectedCommit);
        try {
            boolean alreadyImported = importPackage(pkg);
            audit("IMPORT", SUCCESS, null, null, pkg.packageId());
            return new ImportResult(pkg.packageId(), alreadyImported, parser.recover(pkg));
        } catch (HandoffException e) {
            audit("IMPORT", FAILURE, e.code().name(), null, pkg.packageId());
            throw e;
        } catch (RuntimeException e) {
            audit("IMPORT", FAILURE, "INTERNAL_ERROR", null, pkg.packageId());
            throw e;
        }
    }

    /**
     * 写入导入记录；唯一键冲突时重读并核对已有记录，仅同身份才算幂等成功。
     */
    private boolean importPackage(RecoveredEvidencePackage pkg) {
        try {
            HandoffImportInsert importCmd = new HandoffImportInsert(pkg.packageId(), pkg.service(),
                    pkg.release(), pkg.commit());
            handoffDao.insertImport(importCmd);
            return false;
        } catch (DataIntegrityViolationException e) {
            return reconcileDuplicate(pkg);
        }
    }

    /**
     * 冲突后核对已有记录：无记录（非唯一键完整性异常）或身份不一致必须失败，不得伪装为幂等成功。
     */
    private boolean reconcileDuplicate(RecoveredEvidencePackage pkg) {
        HandoffImport existing = handoffDao.findImportByPackageId(pkg.packageId()).orElse(null);
        if (existing == null) {
            throw new HandoffException(HandoffErrorCode.PACKAGE_INVALID, "import integrity violation");
        }
        if (!Objects.equals(pkg.service(), existing.service())
                || !Objects.equals(pkg.release(), existing.release())
                || !Objects.equals(pkg.commit(), existing.commit())) {
            throw new HandoffException(HandoffErrorCode.VERSION_MISMATCH, "import identity mismatch");
        }
        return true;
    }

    /**
     * 下载 + 校验（fail closed），校验通过返回内容并写 VERIFY 审计。
     */
    private RecoveredEvidencePackage verify(String objectKey, String expectedService, String expectedRelease,
                                            String expectedCommit) {
        requireObsTransport();
        byte[] bytes;
        try {
            bytes = store.retrieve(objectKey);
        } catch (HandoffStoreException e) {
            audit("VERIFY", FAILURE, "STORE_FAILURE", null, null);
            throw new HandoffException(HandoffErrorCode.STORE_FAILURE, "download failed: " + e.code());
        }
        try {
            RecoveredEvidencePackage pkg = verifier.verify(bytes, expectedService, expectedRelease, expectedCommit,
                    config);
            audit("VERIFY", SUCCESS, null, null, pkg.packageId());
            return pkg;
        } catch (HandoffException e) {
            audit("VERIFY", FAILURE, e.code().name(), null, null);
            throw e;
        }
    }

    /**
     * OBS 传输前置检查：显式禁用 → OBS_DISABLED；启用但无真实 adapter → OBS_ADAPTER_UNAVAILABLE；未配置 allow-list。
     */
    private void requireObsTransport() {
        if (!config.obsEnabled()) {
            throw new HandoffException(HandoffErrorCode.OBS_DISABLED, "obs transport disabled");
        }
        if (!store.isEnabled()) {
            throw new HandoffException(HandoffErrorCode.OBS_ADAPTER_UNAVAILABLE, "obs adapter unavailable");
        }
        if (!config.isObsConfigured()) {
            throw new HandoffException(HandoffErrorCode.OBS_NOT_CONFIGURED, "obs allow-list not configured");
        }
    }

    /**
     * 从既有调查状态投影升级输入。
     */
    private EscalationContext buildContext(long investigationId) {
        Investigation inv = requireInvestigation(investigationId);
        Conclusion conclusion = conclusionDao.findByInvestigationId(investigationId).orElse(null);
        List<Hypothesis> hyps = hypothesisDao.findByInvestigationId(investigationId);
        EvidenceBundle bundle = evidenceBundleDao.findBundleJson(investigationId)
                .map(EvidenceBundleCodec::decode).orElse(null);
        String resultType = conclusion == null ? null : conclusion.resultType();
        boolean hasVerifiedSource = bundle != null && bundle.hasVerifiedSource();
        List<String> contradictions = bundle == null || bundle.contradictions() == null
                ? List.of() : bundle.contradictions();
        List<String> missing = missingEvidence(inv, hyps, bundle);
        boolean requiresCodeProof = requiresCodeProof(bundle);
        return new EscalationContext(resultType, highest(hyps), hasVerifiedSource, contradictions, missing,
                requiresCodeProof);
    }

    private List<String> missingEvidence(Investigation inv, List<Hypothesis> hyps, EvidenceBundle bundle) {
        List<String> missing = new ArrayList<>();
        if (inv.status() == InvestigationStatus.INCONCLUSIVE) {
            missing.add("inconclusive");
        }
        for (Hypothesis h : hyps) {
            if (h.missingChecks() != null && !h.missingChecks().isBlank()) {
                missing.add(h.missingChecks());
            }
        }
        if (bundle != null && !bundle.anchors().isEmpty() && !bundle.hasVerifiedSource()) {
            missing.add("source unavailable");
        }
        return missing;
    }

    private boolean requiresCodeProof(EvidenceBundle bundle) {
        return bundle != null && !bundle.anchors().isEmpty() && !bundle.hasVerifiedSource();
    }

    private HypothesisStatus highest(List<Hypothesis> hyps) {
        HypothesisStatus best = null;
        for (Hypothesis h : hyps) {
            if (best == null || rank(h.status()) > rank(best)) {
                best = h.status();
            }
        }
        return best;
    }

    private int rank(HypothesisStatus status) {
        return switch (status) {
            case VALIDATED -> 4;
            case VALIDATING -> 3;
            case PROPOSED -> 2;
            case INCONCLUSIVE -> 1;
            case INVALIDATED -> 0;
        };
    }

    private Map<String, List<String>> buildSections(long investigationId) {
        EvidenceBundle bundle = evidenceBundleDao.findBundleJson(investigationId)
                .map(EvidenceBundleCodec::decode).orElse(null);
        List<Hypothesis> hyps = hypothesisDao.findByInvestigationId(investigationId);
        Map<String, List<String>> sections = new LinkedHashMap<>();
        sections.put("hypotheses", hyps.stream().map(h -> h.status().name() + ": " + h.description()).toList());
        sections.put("contradictions", bundle == null || bundle.contradictions() == null
                ? List.of() : bundle.contradictions());
        sections.put("degradations", bundle == null || bundle.degradations() == null
                ? List.of() : bundle.degradations());
        sections.put("logs", bundle == null ? List.of() : bundle.logEvidences().stream()
                .map(le -> le.summary().template() + " count=" + le.summary().count()).toList());
        sections.put("code-context", bundle == null ? List.of() : bundle.anchors().stream()
                .map(a -> a.type() + ":" + a.value()).toList());
        sections.put("alarm", List.of());
        sections.put("timeline", List.of());
        sections.put("topology", List.of());
        sections.put("metrics", List.of());
        return sections;
    }

    private String timeRange(long investigationId) {
        return evidenceBundleDao.findBundleJson(investigationId).map(EvidenceBundleCodec::decode)
                .map(EvidenceBundle::timeRange).orElse("");
    }

    private Investigation requireInvestigation(long investigationId) {
        return investigationDao.findById(investigationId)
                .orElseThrow(() -> new HandoffException(HandoffErrorCode.INVALID_ARGUMENT, "investigation not found"));
    }

    private HandoffUpload requirePackage(long investigationId, String packageId) {
        requireText(packageId, "packageId");
        HandoffUpload record = handoffDao.findUploadByPackageId(packageId)
                .orElseThrow(() -> new HandoffException(HandoffErrorCode.PACKAGE_INVALID, "package not found"));
        if (record.investigationId() == null || record.investigationId() != investigationId) {
            throw new HandoffException(HandoffErrorCode.INVALID_ARGUMENT, "package does not belong to investigation");
        }
        return record;
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new HandoffException(HandoffErrorCode.INVALID_ARGUMENT, field + " required");
        }
    }

    /**
     * 追加式审计（best-effort：写失败不得改变业务结果）。
     */
    private void audit(String eventType, String result, String errorCode, Long investigationId, String packageId) {
        try {
            handoffDao.recordAudit(eventType, result, errorCode, investigationId, packageId, correlationId());
        } catch (RuntimeException ignored) {
            // 审计写失败 best-effort，不影响业务结果
        }
    }

    private String correlationId() {
        try {
            return MDC.get(CORRELATION_ID_KEY);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
