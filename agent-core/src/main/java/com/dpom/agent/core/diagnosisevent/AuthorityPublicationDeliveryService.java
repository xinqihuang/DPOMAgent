package com.dpom.agent.core.diagnosisevent;

import com.dpom.agent.common.diagnosisevent.DeliveryAcknowledgement;
import com.dpom.agent.common.diagnosisevent.DeliveryOutcome;
import com.dpom.agent.common.diagnosisevent.DiagnosisEventDeliveryPort;
import com.dpom.agent.common.diagnosisevent.DiagnosisEventDeliveryRequest;
import com.dpom.agent.core.persistence.authority.AuthorityTerminalDao;
import com.dpom.agent.core.persistence.authority.PublicationIntentRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/** 从权威终态事务 Outbox 投递同一份冻结 Diagnosis Event v2。 */
public final class AuthorityPublicationDeliveryService {

    private final AuthorityTerminalDao dao;
    private final DiagnosisEventDeliveryPort deliveryPort;
    private final DiagnosisDeliveryPolicy policy;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final String transport;

    /** 创建使用短事务租约和确认的权威 Outbox 工作者。 */
    public AuthorityPublicationDeliveryService(AuthorityTerminalDao dao, DiagnosisEventDeliveryPort deliveryPort,
            DiagnosisDeliveryPolicy policy, Clock clock, ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager, String transport) {
        this.dao = dao;
        this.deliveryPort = deliveryPort;
        this.policy = policy;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transport = transport;
    }

    /** 恢复过期租约并投递一个有界批次，网络调用不持有数据库事务。 */
    public void deliverReady(String workerId) {
        LocalDateTime now = now();
        transactions.executeWithoutResult(status -> dao.recoverExpiredIntents(now));
        for (String intentId : dao.findReadyIntentIds(now, policy.batchSize())) {
            PublicationIntentRow leased = lease(intentId, workerId);
            if (leased != null) {
                deliver(leased);
            }
        }
    }

    /** 授权重放 DEAD 意图；正文、摘要、topic、key 与源关联均保持不变。 */
    public boolean replay(String intentId) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            PublicationIntentRow intent = dao.findIntentById(intentId).orElseThrow();
            if (!contentIntact(intent)) {
                throw new IllegalStateException("CONTENT_INTEGRITY_FAILURE");
            }
            return dao.replayDeadIntent(intentId, now()) == 1;
        }));
    }

    private PublicationIntentRow lease(String intentId, String workerId) {
        return transactions.execute(status -> {
            LocalDateTime acquiredAt = now();
            String token = UUID.randomUUID().toString();
            int changed = dao.acquireIntentLease(intentId, workerId, token,
                    acquiredAt.plus(policy.leaseDuration()), acquiredAt);
            return changed == 1 ? dao.findIntentById(intentId).orElseThrow() : null;
        });
    }

    private void deliver(PublicationIntentRow intent) {
        if (!contentIntact(intent)) {
            finish(intent, DeliveryOutcome.PERMANENT_REJECTION, "CONTENT_INTEGRITY_FAILURE");
            return;
        }
        DeliveryAcknowledgement result;
        try {
            String eventId = objectMapper.readTree(intent.canonicalContent()).path("eventId").asText();
            result = deliveryPort.deliver(new DiagnosisEventDeliveryRequest(eventId, intent.idempotencyKey(),
                    intent.canonicalContent(), intent.canonicalSha256()));
        } catch (Exception exception) {
            result = new DeliveryAcknowledgement(DeliveryOutcome.RETRYABLE_FAILURE, "DELIVERY_IO_ERROR");
        }
        if (result == null || result.outcome() == null) {
            result = new DeliveryAcknowledgement(DeliveryOutcome.RETRYABLE_FAILURE, "MALFORMED_ACKNOWLEDGEMENT");
        }
        finish(intent, result.outcome(), stable(result.errorCode(), result.outcome()));
    }

    private void finish(PublicationIntentRow intent, DeliveryOutcome outcome, String errorCode) {
        transactions.executeWithoutResult(status -> {
            LocalDateTime completedAt = now();
            int changed;
            String recordedOutcome = outcome.name();
            if (outcome == DeliveryOutcome.ACCEPTED || outcome == DeliveryOutcome.EQUIVALENT_DUPLICATE) {
                changed = dao.markIntentDelivered(intent.intentId(), intent.leaseToken(), completedAt);
            } else if (outcome == DeliveryOutcome.RETRYABLE_FAILURE
                    && intent.attemptCount() < policy.maxAttempts()
                    && Duration.between(intent.createdAt(), completedAt).compareTo(policy.maxEventAge()) < 0) {
                Duration delay = retryDelay(intent.attemptCount());
                changed = dao.retryIntent(intent.intentId(), intent.leaseToken(), errorCode,
                        completedAt.plus(delay), completedAt);
            } else {
                if (outcome == DeliveryOutcome.RETRYABLE_FAILURE) {
                    recordedOutcome = "RETRY_EXHAUSTED";
                }
                changed = dao.deadIntent(intent.intentId(), intent.leaseToken(), errorCode, completedAt);
            }
            if (changed == 1) {
                dao.appendIntentAttempt(intent.intentId(), intent.attemptCount(), transport,
                        recordedOutcome, errorCode, completedAt);
            }
        });
    }

    private Duration retryDelay(int attempt) {
        long multiplier = 1L << Math.min(20, Math.max(0, attempt - 1));
        Duration candidate;
        try {
            candidate = policy.baseDelay().multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            candidate = policy.maxDelay();
        }
        return candidate.compareTo(policy.maxDelay()) > 0 ? policy.maxDelay() : candidate;
    }

    private boolean contentIntact(PublicationIntentRow intent) {
        try {
            byte[] content = intent.canonicalContent().getBytes(StandardCharsets.UTF_8);
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            return MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                    intent.canonicalSha256().getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            return false;
        }
    }

    private String stable(String value, DeliveryOutcome outcome) {
        if (value != null && value.matches("[A-Z][A-Z0-9_]{0,63}")) {
            return value;
        }
        return outcome == DeliveryOutcome.IDEMPOTENCY_CONFLICT
                ? "IDEMPOTENCY_CONFLICT" : "REMOTE_UNCLASSIFIED_ERROR";
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC);
    }
}
