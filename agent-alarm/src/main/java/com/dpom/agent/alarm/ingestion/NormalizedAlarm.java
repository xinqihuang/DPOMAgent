package com.dpom.agent.alarm.ingestion;

import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.AlarmStatus;
import com.dpom.agent.common.alarm.SeverityLevel;

import java.time.LocalDateTime;

/**
 * 标准化后的告警：来源标准化器对原始告警事件的统一投影。
 *
 * <p>{@code rawPayload} 保留原始事件全文，确保标准化为无损投影。</p>
 *
 * @param source      来源服务
 * @param externalId  来源侧告警 id（可为空）
 * @param resourceId  资源标识
 * @param alarmName   告警名称
 * @param severity    统一严重度
 * @param status      告警状态
 * @param occurredAt  发生时间
 * @param serviceCode 服务编码（可为空）
 * @param environment 环境标识（可为空）
 * @param tags        标签（可为空）
 * @param rawPayload  原始事件全文（无损保留）
 */
public record NormalizedAlarm(AlarmSource source, String externalId, String resourceId, String alarmName,
                              SeverityLevel severity, AlarmStatus status, LocalDateTime occurredAt,
                              String serviceCode, String environment, String tags, String rawPayload) {
}
