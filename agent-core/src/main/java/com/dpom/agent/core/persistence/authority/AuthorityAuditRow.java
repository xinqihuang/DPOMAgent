package com.dpom.agent.core.persistence.authority;

import java.time.LocalDateTime;

/** MyBatis 追加审计记录。 */
public record AuthorityAuditRow(String auditId, String investigationId, long sequenceNumber,
                                long aggregateVersion, String auditKind, String entityId,
                                String reasonCode, LocalDateTime occurredAt) {
}

