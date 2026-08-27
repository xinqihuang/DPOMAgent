package com.dpom.agent.common.diagnosisevent;

import java.time.OffsetDateTime;

/**
 * 传输无关的 Diagnosis Event v1。
 *
 * @param eventId           事件 UUID
 * @param eventType         事件类型
 * @param schemaVersion     契约版本
 * @param occurredAt        事件发生时间
 * @param producer          生产者信息
 * @param incidentId        事件关联的事件单标识
 * @param investigationId   调查标识
 * @param runId             调查运行标识
 * @param aggregateSequence 聚合序号
 * @param idempotencyKey    幂等键
 * @param provenance        来源版本信息
 * @param inlinePayload     内联载荷，可为空
 * @param artifactRef       受控制品引用，可为空
 */
public record DiagnosisEvent(String eventId, String eventType, String schemaVersion, OffsetDateTime occurredAt,
                             DiagnosisEventProducer producer, String incidentId, String investigationId,
                             String runId, long aggregateSequence, String idempotencyKey,
                             DiagnosisEventProvenance provenance, DiagnosisInlinePayload inlinePayload,
                             DiagnosisArtifactReference artifactRef) {
}
