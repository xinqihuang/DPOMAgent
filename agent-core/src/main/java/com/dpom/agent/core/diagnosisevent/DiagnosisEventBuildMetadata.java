package com.dpom.agent.core.diagnosisevent;

import java.time.OffsetDateTime;

/**
 * 事件创建时由受信任配置和基础设施提供的元数据。
 *
 * @param eventId           事件 UUID
 * @param occurredAt        发生时间
 * @param producerInstanceId 生产实例标识
 * @param aggregateSequence 聚合序号
 */
public record DiagnosisEventBuildMetadata(String eventId, OffsetDateTime occurredAt,
                                          String producerInstanceId, long aggregateSequence) {
}
