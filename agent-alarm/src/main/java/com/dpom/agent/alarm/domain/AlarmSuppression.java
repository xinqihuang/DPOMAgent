package com.dpom.agent.alarm.domain;

import java.time.LocalDateTime;

/**
 * 告警抑制/静默：在指定条件与时间区间内暂停通知。
 *
 * @param id        主键
 * @param kind      抑制类型
 * @param matchKey  匹配键（资源/服务/指纹等）
 * @param reason    原因（可为空）
 * @param startAt   起始时间
 * @param endAt     结束时间
 * @param createdBy 创建人（可为空）
 * @param createdAt 创建时间
 */
public record AlarmSuppression(Long id, SuppressionKind kind, String matchKey, String reason,
                               LocalDateTime startAt, LocalDateTime endAt, String createdBy,
                               LocalDateTime createdAt) {
}
