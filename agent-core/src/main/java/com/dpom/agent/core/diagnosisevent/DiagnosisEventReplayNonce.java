package com.dpom.agent.core.diagnosisevent;

import java.time.LocalDateTime;

/**
 * 防重放 nonce。
 */
public record DiagnosisEventReplayNonce(Long id, String nonce, LocalDateTime expiresAt, LocalDateTime createdAt) {
}
