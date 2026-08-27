package com.dpom.agent.core.persistence.authority;

import java.time.LocalDateTime;

/** 从追加审计事实冻结的 Diagnosis Progress v1 发布意图。 */
public record ProgressPublicationIntentRow(String progressId, String auditId, String investigationId,
                                           String runId, long progressSequence, long aggregateVersion,
                                           String authorityEpoch, String topicName, String idempotencyKey,
                                           String schemaVersion, String canonicalContent,
                                           String canonicalSha256, String status, int attemptCount,
                                           LocalDateTime eligibleAt, LocalDateTime leaseExpiresAt,
                                           String leaseOwner, String leaseToken, String lastErrorCode,
                                           LocalDateTime deliveredAt, LocalDateTime createdAt,
                                           LocalDateTime updatedAt) {
}
