package com.dpom.agent.core.diagnosisevent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 有界重试策略测试。
 */
class DiagnosisRetryPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 21, 14, 30);
    private final DiagnosisDeliveryPolicy policy = new DiagnosisDeliveryPolicy(3, Duration.ofHours(1),
            Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofSeconds(30), 20);
    private final DiagnosisRetryPolicy retry = new DiagnosisRetryPolicy(policy);

    @Test
    void deterministicJitterIsStableExponentialAndCapped() {
        LocalDateTime first = retry.nextAttemptAt("event-1", 1, NOW);
        assertThat(retry.nextAttemptAt("event-1", 1, NOW)).isEqualTo(first);
        assertThat(first).isAfter(NOW).isBeforeOrEqualTo(NOW.plusSeconds(10));
        assertThat(retry.nextAttemptAt("event-1", 20, NOW)).isBeforeOrEqualTo(NOW.plusMinutes(1));
    }

    @Test
    void attemptAndAgeLimitsAreInclusive() {
        DiagnosisEventOutbox event = event(3, NOW.minusHours(1));
        assertThat(retry.attemptsExhausted(event)).isTrue();
        assertThat(retry.ageExhausted(event, NOW)).isTrue();
    }

    @Test
    void rejectsNonPositiveOrInvertedBounds() {
        assertThatThrownBy(() -> new DiagnosisDeliveryPolicy(0, Duration.ofHours(1), Duration.ofSeconds(1),
                Duration.ofSeconds(2), Duration.ofSeconds(1), 1)).hasMessage("INVALID_DELIVERY_POLICY");
        assertThatThrownBy(() -> new DiagnosisDeliveryPolicy(1, Duration.ofHours(1), Duration.ofSeconds(3),
                Duration.ofSeconds(2), Duration.ofSeconds(1), 1)).hasMessage("INVALID_DELIVERY_POLICY");
    }

    private DiagnosisEventOutbox event(int attempts, LocalDateTime createdAt) {
        return new DiagnosisEventOutbox(1L, "event-1", "idem-1", 1L, 1L, "investigation.completed",
                1, "1.0", "{}", "a".repeat(64), DiagnosisOutboxStatus.PENDING, attempts, NOW,
                null, null, null, null, null, createdAt, createdAt);
    }
}
