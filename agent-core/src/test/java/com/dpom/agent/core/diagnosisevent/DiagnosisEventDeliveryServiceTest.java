package com.dpom.agent.core.diagnosisevent;

import com.dpom.agent.common.diagnosisevent.DeliveryAcknowledgement;
import com.dpom.agent.common.diagnosisevent.DeliveryOutcome;
import com.dpom.agent.common.diagnosisevent.DiagnosisEventDeliveryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Diagnosis Event 投递编排结果映射测试。
 */
class DiagnosisEventDeliveryServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 21, 14, 30);

    private final DiagnosisEventLeaseService leaseService = mock(DiagnosisEventLeaseService.class);
    private final DiagnosisEventDeliveryPort deliveryPort = mock(DiagnosisEventDeliveryPort.class);
    private final DiagnosisEventStateService stateService = mock(DiagnosisEventStateService.class);
    private final DiagnosisEventMetrics metrics = mock(DiagnosisEventMetrics.class);
    private DiagnosisEventDeliveryService service;

    @BeforeEach
    void setUp() {
        DiagnosisDeliveryPolicy policy = new DiagnosisDeliveryPolicy(3, Duration.ofDays(1),
                Duration.ofSeconds(2), Duration.ofMinutes(1), Duration.ofMinutes(1), 10);
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new DiagnosisEventDeliveryService(leaseService, deliveryPort, stateService, policy, clock, metrics);
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryOutcome.class, names = {"ACCEPTED", "EQUIVALENT_DUPLICATE"})
    void acceptedAndEquivalentDuplicateBecomeDelivered(DeliveryOutcome outcome) {
        DiagnosisEventOutbox event = event(1, 1, NOW.minusMinutes(1), "{}");
        when(leaseService.leaseReady("worker-1")).thenReturn(List.of(event));
        when(deliveryPort.deliver(any())).thenReturn(new DeliveryAcknowledgement(outcome, null));

        service.deliverReady("worker-1");

        verify(stateService).markDelivered(event, outcome.name(), NOW);
    }

    @Test
    void retryableAcknowledgementAndTimeoutScheduleBoundedRetry() {
        DiagnosisEventOutbox acknowledged = event(2, 1, NOW.minusMinutes(1), "{}");
        DiagnosisEventOutbox timeout = event(3, 1, NOW.minusMinutes(1), "{\"a\":1}");
        when(leaseService.leaseReady("worker-1")).thenReturn(List.of(acknowledged, timeout));
        when(deliveryPort.deliver(any()))
                .thenReturn(new DeliveryAcknowledgement(DeliveryOutcome.RETRYABLE_FAILURE, "HTTP_429"))
                .thenThrow(new IllegalStateException("secret-and-body-must-not-propagate"));

        service.deliverReady("worker-1");

        verify(stateService).scheduleRetry(eq(acknowledged), eq("HTTP_429"), any(), eq(NOW));
        verify(stateService).scheduleRetry(eq(timeout), eq("DELIVERY_IO_ERROR"), any(), eq(NOW));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP_408", "HTTP_429", "HTTP_500"})
    void boundedHttpFailuresRemainRetryable(String errorCode) {
        DiagnosisEventOutbox event = event(20, 1, NOW.minusMinutes(1), "{}");
        when(leaseService.leaseReady("worker-1")).thenReturn(List.of(event));
        when(deliveryPort.deliver(any())).thenReturn(
                new DeliveryAcknowledgement(DeliveryOutcome.RETRYABLE_FAILURE, errorCode));

        service.deliverReady("worker-1");

        verify(stateService).scheduleRetry(eq(event), eq(errorCode), any(), eq(NOW));
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryOutcome.class, names = {"PERMANENT_REJECTION", "IDEMPOTENCY_CONFLICT"})
    void permanentAndIdempotencyConflictBecomeDead(DeliveryOutcome outcome) {
        DiagnosisEventOutbox event = event(4, 1, NOW.minusMinutes(1), "{}");
        when(leaseService.leaseReady("worker-1")).thenReturn(List.of(event));
        when(deliveryPort.deliver(any())).thenReturn(new DeliveryAcknowledgement(outcome, "HTTP_400"));

        service.deliverReady("worker-1");

        verify(stateService).markDead(event, outcome.name(), "HTTP_400", NOW);
    }

    @Test
    void malformedAndOversizedAcknowledgementsRetryOnlyWithinBudget() {
        DiagnosisEventOutbox malformed = event(5, 2, NOW.minusMinutes(1), "{}");
        DiagnosisEventOutbox oversized = event(6, 3, NOW.minusMinutes(1), "{}");
        when(leaseService.leaseReady("worker-1")).thenReturn(List.of(malformed, oversized));
        when(deliveryPort.deliver(any())).thenReturn(null)
                .thenReturn(new DeliveryAcknowledgement(DeliveryOutcome.RETRYABLE_FAILURE,
                        "ACKNOWLEDGEMENT_TOO_LARGE"));

        service.deliverReady("worker-1");

        verify(stateService).scheduleRetry(eq(malformed), eq("MALFORMED_ACKNOWLEDGEMENT"), any(), eq(NOW));
        verify(stateService).markDead(oversized, "RETRY_EXHAUSTED", "ACKNOWLEDGEMENT_TOO_LARGE", NOW);
    }

    @Test
    void contentIntegrityFailureIsDeadBeforeNetwork() {
        DiagnosisEventOutbox corrupt = eventWithHash(7, 1, NOW.minusMinutes(1), "sensitive-evidence", "0".repeat(64));
        when(leaseService.leaseReady("worker-1")).thenReturn(List.of(corrupt));

        service.deliverReady("worker-1");

        verify(deliveryPort, never()).deliver(any());
        verify(stateService).markDead(corrupt, "INTEGRITY_FAILURE", "CONTENT_INTEGRITY_FAILURE", NOW);
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(metrics).record(eq("DEAD"), eq("INTEGRITY_FAILURE"), error.capture());
        assertThat(error.getValue()).isEqualTo("CONTENT_INTEGRITY_FAILURE");
    }

    private DiagnosisEventOutbox event(long id, int attempts, LocalDateTime createdAt, String content) {
        return eventWithHash(id, attempts, createdAt, content, sha256(content));
    }

    private DiagnosisEventOutbox eventWithHash(long id, int attempts, LocalDateTime createdAt,
                                                String content, String hash) {
        return new DiagnosisEventOutbox(id, "event-" + id, "idem-" + id, 100L + id, 200L + id,
                "investigation.completed", 1, "1.0", content, hash, DiagnosisOutboxStatus.IN_FLIGHT,
                attempts, NOW, "worker-1", "token-" + id, NOW.plusMinutes(1), null, null, createdAt, NOW);
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
