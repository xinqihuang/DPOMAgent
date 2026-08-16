package com.dpom.agent.web.dto;

/**
 * 审批决定请求（批准或拒绝共用）：不携带 approval 布尔到上传动作，审批引用与理由必填。
 *
 * @param packageId  包标识
 * @param approverRef 外部审批引用（必填，当前无身份系统）
 * @param reason     审批/拒绝理由（必填）
 */
public record ApprovalRequest(String packageId, String approverRef, String reason) {
}
