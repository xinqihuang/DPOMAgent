package com.dpom.agent.alarm.domain;

import java.time.LocalDateTime;

/**
 * 告警审计条目：通知、认领、抑制、静默、处置工件与状态变更的可审计记录。
 *
 * @param id         主键
 * @param action     动作
 * @param targetType 目标类型
 * @param targetId   目标 id（可为空）
 * @param operator   操作人（可为空）
 * @param detail     详情（可为空）
 * @param result     结果（可为空）
 * @param createdAt  创建时间
 */
public record AlarmAudit(Long id, String action, String targetType, Long targetId, String operator,
                         String detail, String result, LocalDateTime createdAt) {
}
