package com.dpom.agent.core.persistence.authority;

import java.time.LocalDateTime;

/** MyBatis 不可变终态诊断源记录。 */
public record DiagnosisSourceRow(String sourceId, String investigationId, long aggregateVersion,
                                 String contractVersion, String sourceJson, String sourceSha256,
                                 String documentSha256, LocalDateTime committedAt) {
}
