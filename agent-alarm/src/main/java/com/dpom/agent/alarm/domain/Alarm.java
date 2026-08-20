package com.dpom.agent.alarm.domain;

import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.AlarmStatus;
import com.dpom.agent.common.alarm.SeverityLevel;

import java.time.LocalDateTime;

/**
 * 统一告警：各源告警经标准化与治理后的无损能投影。
 *
 * @param id               主键
 * @param source           来源服务
 * @param ingestionMode    接入方式（webhook/poll）
 * @param externalId       来源侧告警 id（可为空）
 * @param fingerprint      去重指纹
 * @param resourceId       资源标识
 * @param alarmName        告警名称
 * @param severity         统一严重度
 * @param status           告警状态
 * @param occurrenceCount  发生计数
 * @param firstOccurredAt  首次发生时间
 * @param lastOccurredAt   最近发生时间
 * @param ingestedAt       接入时间
 * @param serviceCode      服务编码（可为空）
 * @param environment      环境标识（可为空）
 * @param rawPayload       原始字段集合（JSON，无损）
 * @param samplePayloads   压缩采样（JSON，可为空）
 */
public record Alarm(Long id, AlarmSource source, String ingestionMode, String externalId, String fingerprint,
                    String resourceId, String alarmName, SeverityLevel severity, AlarmStatus status,
                    int occurrenceCount, LocalDateTime firstOccurredAt, LocalDateTime lastOccurredAt,
                    LocalDateTime ingestedAt, String serviceCode, String environment,
                    String rawPayload, String samplePayloads) {
}
