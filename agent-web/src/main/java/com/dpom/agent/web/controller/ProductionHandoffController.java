package com.dpom.agent.web.controller;

import com.dpom.agent.core.handoff.ApprovalResult;
import com.dpom.agent.core.handoff.BuiltPackage;
import com.dpom.agent.core.handoff.EscalationDecision;
import com.dpom.agent.core.handoff.EvidenceHandoffService;
import com.dpom.agent.web.dto.ApprovalRequest;
import com.dpom.agent.web.dto.ApprovalResponse;
import com.dpom.agent.web.dto.EscalationDecisionResponse;
import com.dpom.agent.web.dto.HandoffPackageResponse;
import com.dpom.agent.web.dto.UploadRequest;
import com.dpom.agent.web.dto.UploadResponse;
import com.dpom.agent.web.metrics.HandoffMetrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生产区域证据交接控制器：升级判定、打包、审批、审批后上传。仅 production Profile 装配。
 */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "dpom.handoff.mode", havingValue = "production")
public class ProductionHandoffController {

    private final EvidenceHandoffService service;
    private final HandoffMetrics metrics;

    /**
     * 构造器注入。
     *
     * @param service 交接服务
     * @param metrics 交接指标（best-effort）
     */
    public ProductionHandoffController(EvidenceHandoffService service, HandoffMetrics metrics) {
        this.service = service;
        this.metrics = metrics;
    }

    /**
     * 计算并持久化升级判定。
     */
    @GetMapping("/investigations/{id}/escalation")
    public EscalationDecisionResponse escalation(@PathVariable("id") long id) {
        EscalationDecision decision = service.escalate(id);
        recordEscalation(decision.eligible());
        return new EscalationDecisionResponse(decision.eligible(),
                decision.reasons().stream().map(Enum::name).toList(), decision.missingEvidence(),
                decision.confidence());
    }

    /**
     * 构建证据包。
     */
    @PostMapping("/investigations/{id}/handoff/package")
    public HandoffPackageResponse buildPackage(@PathVariable("id") long id) {
        BuiltPackage pkg = service.buildPackage(id);
        return new HandoffPackageResponse(pkg.packageId(), pkg.checksum(), pkg.sizeBytes());
    }

    /**
     * 批准上传（独立审批决定，绑定 packageId）。
     */
    @PostMapping("/investigations/{id}/handoff/approve")
    public ApprovalResponse approve(@PathVariable("id") long id, @RequestBody ApprovalRequest request) {
        ApprovalResult result = service.approveUpload(id, request.packageId(), request.approverRef(), request.reason());
        return new ApprovalResponse(result.packageId(), result.status().name(), result.approvedAt());
    }

    /**
     * 拒绝上传（独立审批决定，绑定 packageId）。
     */
    @PostMapping("/investigations/{id}/handoff/reject")
    public ApprovalResponse reject(@PathVariable("id") long id, @RequestBody ApprovalRequest request) {
        ApprovalResult result = service.rejectUpload(id, request.packageId(), request.approverRef(), request.reason());
        return new ApprovalResponse(result.packageId(), result.status().name(), result.approvedAt());
    }

    /**
     * 上传（只读数据库既有 APPROVED 状态，不接收 approval 布尔）。
     */
    @PostMapping("/investigations/{id}/handoff/upload")
    public UploadResponse upload(@PathVariable("id") long id, @RequestBody UploadRequest request) {
        String objectKey = service.upload(id, request.packageId());
        recordUpload(true);
        return new UploadResponse(objectKey);
    }

    private void recordEscalation(boolean eligible) {
        try {
            metrics.recordEscalation(eligible);
        } catch (RuntimeException ignored) {
            // 指标失败不得改变业务结果
        }
    }

    private void recordUpload(boolean success) {
        try {
            metrics.recordUpload(success);
        } catch (RuntimeException ignored) {
            // 指标失败不得改变业务结果
        }
    }
}
