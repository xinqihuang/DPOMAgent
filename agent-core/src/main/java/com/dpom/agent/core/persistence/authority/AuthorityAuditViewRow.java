package com.dpom.agent.core.persistence.authority;

import java.time.LocalDateTime;

/** 只读进度 API 使用的安全审计行。 */
public record AuthorityAuditViewRow(String auditId, String investigationId, long sequenceNumber,
                                    long aggregateVersion, String auditKind, String entityId,
                                    String reasonCode, LocalDateTime occurredAt) {
}
