package com.dpom.agent.alarm.ingestion;

import java.time.LocalDateTime;

/**
 * 来源侧原始告警事件：轮询网关返回的增量事件载体。
 *
 * @param externalId  来源侧告警 id（可为空）
 * @param occurredAt  发生时间
 * @param rawPayload  原始事件全文
 */
public record RawAlarmEvent(String externalId, LocalDateTime occurredAt, String rawPayload) {
}
