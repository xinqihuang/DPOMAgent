package com.dpom.agent.alarm.domain;

import java.time.LocalDateTime;

/**
 * 通知发送记录：每条通知的发送结果与时间。
 *
 * @param id           主键
 * @param incidentId   事件 id
 * @param ruleId       规则 id（可为空）
 * @param channel      渠道
 * @param recipient    接收方（可为空）
 * @param status       发送状态
 * @param errorMessage 错误信息（可为空）
 * @param sentAt       发送时间（可为空）
 * @param createdAt    创建时间
 */
public record NotificationRecord(Long id, Long incidentId, Long ruleId, NotificationChannel channel,
                                 String recipient, NotificationStatus status, String errorMessage,
                                 LocalDateTime sentAt, LocalDateTime createdAt) {
}
