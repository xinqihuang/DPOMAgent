package com.dpom.agent.core.handoff;

import java.time.LocalDateTime;

/**
 * 审批动作结果（批准或拒绝）。
 *
 * @param packageId 包标识
 * @param status    审批状态
 * @param approvedAt 批准时间（拒绝时为 null）
 */
public record ApprovalResult(String packageId, UploadApprovalStatus status, LocalDateTime approvedAt) {
}
