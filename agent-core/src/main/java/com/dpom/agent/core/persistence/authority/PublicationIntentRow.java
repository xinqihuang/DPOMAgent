package com.dpom.agent.core.persistence.authority;

import java.time.LocalDateTime;

/** 尚未绑定传输方式的终态发布意图。 */
public record PublicationIntentRow(String intentId, String investigationId, long aggregateSequence,
                                   String eventType, String sourceId, String sourceSha256,
                                   String status, LocalDateTime eligibleAt, LocalDateTime createdAt) {
}

