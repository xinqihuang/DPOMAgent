package com.dpom.agent.core.diagnosisevent;

import java.time.LocalDateTime;

/**
 * 持久化的不可变 Diagnosis Event 与投递状态。
 */
public record DiagnosisEventOutbox(Long id, String eventId, String idempotencyKey, Long investigationId,
                                   Long runId, String eventType, long aggregateSequence, String schemaVersion,
                                   String canonicalContent, String canonicalSha256, DiagnosisOutboxStatus status,
                                   int attemptCount, LocalDateTime nextAttemptAt, String leaseOwner,
                                   String leaseToken, LocalDateTime leaseExpiresAt, String lastErrorCode,
                                   LocalDateTime deliveredAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
