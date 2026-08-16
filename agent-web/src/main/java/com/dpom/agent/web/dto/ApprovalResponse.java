package com.dpom.agent.web.dto;

import java.time.LocalDateTime;

/**
 * 审批动作响应。
 *
 * @param packageId  包标识
 * @param status     审批状态
 * @param approvedAt 批准时间（拒绝时为 null）
 */
public record ApprovalResponse(String packageId, String status, LocalDateTime approvedAt) {
}
