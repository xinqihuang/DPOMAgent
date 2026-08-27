package com.dpom.agent.core.persistence.authority;

import java.time.LocalDateTime;

/** 尚未绑定传输方式、已经冻结规范正文的终态发布意图。 */
public record PublicationIntentRow(String intentId, String investigationId, long aggregateSequence,
                                   String eventType, String sourceId, String sourceSha256,
                                   String topicName, String idempotencyKey, String schemaVersion,
                                   String canonicalContent, String canonicalSha256, String status,
                                   int attemptCount, LocalDateTime eligibleAt, LocalDateTime leaseExpiresAt,
                                   String leaseOwner, String leaseToken, String lastErrorCode,
                                   LocalDateTime deliveredAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
