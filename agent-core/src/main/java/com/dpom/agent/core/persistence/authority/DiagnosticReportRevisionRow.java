package com.dpom.agent.core.persistence.authority;

import java.time.LocalDateTime;

/** 不可变 diagnosis-only 规范报告修订。 */
public record DiagnosticReportRevisionRow(String reportId, String investigationId, String diagnosisSourceId,
                                          String requestIdempotencyKey, String requestFingerprint,
                                          long revisionNumber, String supersedesReportId,
                                          String changeReasonsJson, String canonicalContent,
                                          String reportDigest, String sourceDigest,
                                          LocalDateTime createdAt) {
}
