package com.dpom.agent.core.diagnosisevent;

import java.time.Duration;

/**
 * 有界投递、租约和重试策略。
 */
public record DiagnosisDeliveryPolicy(int maxAttempts, Duration maxEventAge, Duration baseDelay,
                                      Duration maxDelay, Duration leaseDuration, int batchSize) {

    /**
     * 校验所有边界为正且上下界一致。
     */
    public DiagnosisDeliveryPolicy {
        if (maxAttempts < 1 || batchSize < 1 || !positive(maxEventAge) || !positive(baseDelay)
                || !positive(maxDelay) || !positive(leaseDuration) || baseDelay.compareTo(maxDelay) > 0) {
            throw new IllegalArgumentException("INVALID_DELIVERY_POLICY");
        }
    }

    private static boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }
}
