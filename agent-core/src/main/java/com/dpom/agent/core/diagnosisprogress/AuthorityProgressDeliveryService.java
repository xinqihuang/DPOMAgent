package com.dpom.agent.core.diagnosisprogress;

import com.dpom.agent.common.diagnosisevent.DeliveryAcknowledgement;
import com.dpom.agent.common.diagnosisevent.DeliveryOutcome;
import com.dpom.agent.common.diagnosisprogress.DiagnosisProgressDeliveryPort;
import com.dpom.agent.common.diagnosisprogress.DiagnosisProgressDeliveryRequest;
import com.dpom.agent.core.diagnosisevent.DiagnosisDeliveryPolicy;
import com.dpom.agent.core.persistence.authority.AuthorityProgressDao;
import com.dpom.agent.core.persistence.authority.ProgressPublicationIntentRow;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

/** 以每个 Investigation 严格顺序投递冻结的 Diagnosis Progress Outbox。 */
public final class AuthorityProgressDeliveryService {

    private final AuthorityProgressDao dao;
    private final DiagnosisProgressDeliveryPort deliveryPort;
    private final DiagnosisDeliveryPolicy policy;
    private final Clock clock;
    private final TransactionTemplate transactions;

    /** 创建使用短事务租约、网络事务外发送和 fencing acknowledgement 的工作者。 */
    public AuthorityProgressDeliveryService(AuthorityProgressDao dao, DiagnosisProgressDeliveryPort deliveryPort,
            DiagnosisDeliveryPolicy policy, Clock clock, PlatformTransactionManager transactionManager) {
        this.dao = dao;
        this.deliveryPort = deliveryPort;
        this.policy = policy;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** 恢复过期租约并投递一个有界批次。 */
    public void deliverReady(String workerId) {
        LocalDateTime now = now();
        transactions.executeWithoutResult(status -> dao.recoverExpiredIntents(now));
        for (String progressId : dao.findReadyIntentIds(now, policy.batchSize())) {
            ProgressPublicationIntentRow leased = lease(progressId, workerId);
            if (leased != null) {
                deliver(leased);
            }
        }
    }

    /** 从 DEAD 状态授权字节保持不变的重放。 */
    public boolean replay(String progressId) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            ProgressPublicationIntentRow intent = dao.findIntent(progressId).orElseThrow();
            if (!contentIntact(intent)) {
                throw new IllegalStateException("CONTENT_INTEGRITY_FAILURE");
            }
            return dao.replayDeadIntent(progressId, now()) == 1;
        }));
    }

    private ProgressPublicationIntentRow lease(String progressId, String workerId) {
        return transactions.execute(status -> {
            LocalDateTime acquiredAt = now();
            String token = UUID.randomUUID().toString();
            int changed = dao.acquireIntentLease(progressId, workerId, token,
                    acquiredAt.plus(policy.leaseDuration()), acquiredAt);
            return changed == 1 ? dao.findIntent(progressId).orElseThrow() : null;
        });
    }

    private void deliver(ProgressPublicationIntentRow intent) {
        if (!contentIntact(intent)) {
            finish(intent, DeliveryOutcome.PERMANENT_REJECTION, "CONTENT_INTEGRITY_FAILURE");
            return;
        }
        DeliveryAcknowledgement result;
        try {
            result = deliveryPort.deliver(new DiagnosisProgressDeliveryRequest(intent.progressId(),
                    intent.investigationId(), intent.canonicalContent(), intent.canonicalSha256()));
        } catch (Exception exception) {
            result = new DeliveryAcknowledgement(DeliveryOutcome.RETRYABLE_FAILURE, "DELIVERY_IO_ERROR");
        }
        if (result == null || result.outcome() == null) {
            result = new DeliveryAcknowledgement(DeliveryOutcome.RETRYABLE_FAILURE,
                    "MALFORMED_ACKNOWLEDGEMENT");
        }
        finish(intent, result.outcome(), stable(result.errorCode(), result.outcome()));
    }

    private void finish(ProgressPublicationIntentRow intent, DeliveryOutcome outcome, String errorCode) {
        transactions.executeWithoutResult(status -> {
            LocalDateTime completedAt = now();
            int changed;
            String recordedOutcome = outcome.name();
            if (outcome == DeliveryOutcome.ACCEPTED || outcome == DeliveryOutcome.EQUIVALENT_DUPLICATE) {
                changed = dao.markIntentDelivered(intent.progressId(), intent.leaseToken(), completedAt);
            } else if (outcome == DeliveryOutcome.RETRYABLE_FAILURE
                    && intent.attemptCount() < policy.maxAttempts()
                    && Duration.between(intent.createdAt(), completedAt).compareTo(policy.maxEventAge()) < 0) {
                changed = dao.retryIntent(intent.progressId(), intent.leaseToken(), errorCode,
                        completedAt.plus(retryDelay(intent.attemptCount())), completedAt);
            } else {
                if (outcome == DeliveryOutcome.RETRYABLE_FAILURE) {
                    recordedOutcome = "RETRY_EXHAUSTED";
                }
                changed = dao.deadIntent(intent.progressId(), intent.leaseToken(), errorCode, completedAt);
            }
            if (changed == 1) {
                dao.appendIntentAttempt(intent.progressId(), intent.attemptCount(), recordedOutcome,
                        errorCode, completedAt);
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

    private boolean contentIntact(ProgressPublicationIntentRow intent) {
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
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
