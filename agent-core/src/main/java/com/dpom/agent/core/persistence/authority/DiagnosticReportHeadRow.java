package com.dpom.agent.core.persistence.authority;

import java.time.LocalDateTime;

/** Diagnosis-only 报告流的当前修订指针。 */
public record DiagnosticReportHeadRow(String investigationId, String latestReportId,
                                      long latestRevision, long lockVersion,
                                      LocalDateTime createdAt, LocalDateTime updatedAt) {
}
