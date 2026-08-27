package com.dpom.agent.core.diagnosisevent;

import java.time.LocalDateTime;

/**
 * Diagnosis Event 的追加式审计记录。
 */
public record DiagnosisEventAudit(Long id, String eventId, String eventType, String action, String result,
                                  String errorCode, String operatorRef, String reason, String correlationId,
                                  LocalDateTime createdAt) {
}
