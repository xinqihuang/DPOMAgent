package com.dpom.agent.core.diagnosisevent;

import com.dpom.agent.common.diagnosisevent.DeliveryAcknowledgement;
import com.dpom.agent.common.diagnosisevent.DeliveryOutcome;
import com.dpom.agent.common.diagnosisevent.DiagnosisEventDeliveryPort;
import com.dpom.agent.common.diagnosisevent.DiagnosisEventDeliveryRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 在数据库租约之外执行网络投递并映射有界结果。
 */
public final class DiagnosisEventDeliveryService {

    private final DiagnosisEventLeaseService leaseService;
    private final DiagnosisEventDeliveryPort deliveryPort;
    private final DiagnosisEventStateService stateService;
    private final DiagnosisRetryPolicy retryPolicy;
    private final Clock clock;
    private final DiagnosisEventMetrics metrics;

    /** 创建投递编排服务。 */
    public DiagnosisEventDeliveryService(DiagnosisEventLeaseService leaseService,
                                         DiagnosisEventDeliveryPort deliveryPort,
                                         DiagnosisEventStateService stateService,
                                         DiagnosisDeliveryPolicy policy, Clock clock,
                                         DiagnosisEventMetrics metrics) {
        this.leaseService = leaseService;
        this.deliveryPort = deliveryPort;
        this.stateService = stateService;
        this.retryPolicy = new DiagnosisRetryPolicy(policy);
        this.clock = clock;
        this.metrics = metrics;
    }

    /** 投递当前工作者获取的有界批次。 */
    public void deliverReady(String workerId) {
        for (DiagnosisEventOutbox event : leaseService.leaseReady(workerId)) {
            deliverOne(event);
        }
    }

    private void deliverOne(DiagnosisEventOutbox event) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!contentIntact(event)) {
            dead(event, "INTEGRITY_FAILURE", "CONTENT_INTEGRITY_FAILURE", now);
            return;
        }
        DeliveryAcknowledgement acknowledgement;
        try {
            acknowledgement = deliveryPort.deliver(new DiagnosisEventDeliveryRequest(event.eventId(),
                    event.idempotencyKey(), event.canonicalContent(), event.canonicalSha256()));
        } catch (RuntimeException exception) {
            retry(event, "DELIVERY_IO_ERROR", now);
            return;
        }
        mapAcknowledgement(event, acknowledgement, now);
    }

    private void mapAcknowledgement(DiagnosisEventOutbox event, DeliveryAcknowledgement acknowledgement,
                                    LocalDateTime now) {
        if (acknowledgement == null || acknowledgement.outcome() == null) {
            retry(event, "MALFORMED_ACKNOWLEDGEMENT", now);
            return;
        }
        DeliveryOutcome outcome = acknowledgement.outcome();
        String errorCode = stableErrorCode(acknowledgement.errorCode(), outcome);
        switch (outcome) {
            case ACCEPTED, EQUIVALENT_DUPLICATE -> delivered(event, outcome.name(), now);
            case RETRYABLE_FAILURE -> retry(event, errorCode, now);
            case PERMANENT_REJECTION, IDEMPOTENCY_CONFLICT -> dead(event, outcome.name(), errorCode, now);
            default -> retry(event, "MALFORMED_ACKNOWLEDGEMENT", now);
        }
    }

    private void delivered(DiagnosisEventOutbox event, String result, LocalDateTime now) {
        stateService.markDelivered(event, result, now);
        metric("DELIVERED", result, null);
    }

    private void retry(DiagnosisEventOutbox event, String errorCode, LocalDateTime now) {
        if (retryPolicy.attemptsExhausted(event) || retryPolicy.ageExhausted(event, now)) {
            dead(event, "RETRY_EXHAUSTED", errorCode, now);
            return;
        }
        LocalDateTime next = retryPolicy.nextAttemptAt(event.eventId(), event.attemptCount(), now);
        stateService.scheduleRetry(event, errorCode, next, now);
        metric("PENDING", "RETRYABLE_FAILURE", errorCode);
    }

    private void dead(DiagnosisEventOutbox event, String result, String errorCode, LocalDateTime now) {
        stateService.markDead(event, result, errorCode, now);
        metric("DEAD", result, errorCode);
    }

    private boolean contentIntact(DiagnosisEventOutbox event) {
        try {
            byte[] content = event.canonicalContent().getBytes(StandardCharsets.UTF_8);
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            return MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                    event.canonicalSha256().getBytes(StandardCharsets.US_ASCII));
        } catch (RuntimeException | java.security.NoSuchAlgorithmException exception) {
            return false;
        }
    }

    private String stableErrorCode(String value, DeliveryOutcome outcome) {
        if (value != null && value.matches("[A-Z][A-Z0-9_]{0,63}")) {
            return value;
        }
        return outcome == DeliveryOutcome.IDEMPOTENCY_CONFLICT
                ? "IDEMPOTENCY_CONFLICT" : "REMOTE_UNCLASSIFIED_ERROR";
    }

    private void metric(String state, String result, String errorCode) {
        try {
            metrics.record(state, result, errorCode);
        } catch (RuntimeException ignored) {
            // 指标是 best-effort，不影响业务状态。
        }
    }
}
