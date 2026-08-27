package com.dpom.agent.web;

import com.dpom.agent.core.diagnosisevent.DiagnosisEventOutbox;
import com.dpom.agent.core.diagnosisevent.DiagnosisDeliveryPolicy;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventLeaseService;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventStateService;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventReplayService;
import com.dpom.agent.core.diagnosisevent.DiagnosisOutboxStatus;
import com.dpom.agent.core.persistence.DiagnosisEventAuditDao;
import com.dpom.agent.core.persistence.DiagnosisEventOutboxDao;
import com.dpom.agent.core.persistence.DiagnosisReplayNonceDao;
import com.dpom.agent.core.persistence.command.DiagnosisEventAuditInsert;
import com.dpom.agent.core.persistence.command.DiagnosisEventLeaseCommand;
import com.dpom.agent.core.persistence.command.DiagnosisEventOutboxInsert;
import com.dpom.agent.core.persistence.command.DiagnosisEventTransitionCommand;
import com.dpom.agent.core.persistence.command.DiagnosisReplayNonceInsert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.HexFormat;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * Diagnosis Event 发件箱的 H2 MySQL 模式持久化测试。
 */
@SpringBootTest
class DiagnosisEventPersistenceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 21, 14, 30);

    @Autowired
    private DiagnosisEventOutboxDao outboxDao;
    @MockitoSpyBean
    private DiagnosisEventAuditDao auditDao;
    @Autowired
    private DiagnosisReplayNonceDao nonceDao;
    @Autowired
    private DiagnosisEventStateService stateService;
    @Autowired
    private DiagnosisEventReplayService replayService;

    @AfterEach
    void resetAuditSpy() {
        reset(auditDao);
    }

    @Test
    void insertsReadsAndEnforcesAllImmutableIdentityKeys() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        DiagnosisEventOutboxInsert original = command(UUID.randomUUID().toString(), "idem-" + suffix, 1001, 2001);
        assertThat(outboxDao.insert(original)).isOne();
        DiagnosisEventOutbox stored = outboxDao.findById(original.getId()).orElseThrow();
        assertThat(stored.status()).isEqualTo(DiagnosisOutboxStatus.PENDING);
        assertThat(outboxDao.findByEventId(original.getEventId())).contains(stored);
        assertThat(outboxDao.findByIdempotencyKey(original.getIdempotencyKey())).contains(stored);

        assertDuplicate(command(original.getEventId(), "other-" + suffix, 1002, 2002));
        assertDuplicate(command(UUID.randomUUID().toString(), original.getIdempotencyKey(), 1003, 2003));
        assertDuplicate(command(UUID.randomUUID().toString(), "third-" + suffix, 1001, 2001));
    }

    @Test
    void transitionsWithFencingAndNeverUpdatesCanonicalContent() {
        DiagnosisEventOutboxInsert insert = command(UUID.randomUUID().toString(),
                "lease-" + UUID.randomUUID(), 1101, 2101);
        outboxDao.insert(insert);
        String content = insert.getCanonicalContent();
        String token = UUID.randomUUID().toString();

        assertThat(outboxDao.acquireLease(new DiagnosisEventLeaseCommand(
                insert.getId(), NOW, "worker-1", token, NOW.plusMinutes(1)))).isOne();
        DiagnosisEventOutbox leased = outboxDao.findById(insert.getId()).orElseThrow();
        assertThat(leased.status()).isEqualTo(DiagnosisOutboxStatus.IN_FLIGHT);
        assertThat(leased.attemptCount()).isOne();
        assertThat(outboxDao.markDelivered(new DiagnosisEventTransitionCommand(
                insert.getId(), "stale-token", NOW.plusSeconds(1), null, null))).isZero();
        assertThat(outboxDao.markDelivered(new DiagnosisEventTransitionCommand(
                insert.getId(), token, NOW.plusSeconds(1), null, null))).isOne();

        DiagnosisEventOutbox delivered = outboxDao.findById(insert.getId()).orElseThrow();
        assertThat(delivered.status()).isEqualTo(DiagnosisOutboxStatus.DELIVERED);
        assertThat(delivered.canonicalContent()).isEqualTo(content);
        assertThat(delivered.canonicalSha256()).isEqualTo(insert.getCanonicalSha256());
    }

    @Test
    void onlyOneWorkerWinsLeaseAndExpiredLeaseCanBeRecovered() throws Exception {
        DiagnosisEventOutboxInsert insert = command(UUID.randomUUID().toString(),
                "race-" + UUID.randomUUID(), 1201, 2201);
        outboxDao.insert(insert);
        Callable<Integer> first = () -> outboxDao.acquireLease(new DiagnosisEventLeaseCommand(
                insert.getId(), NOW, "worker-a", "token-a", NOW.plusSeconds(10)));
        Callable<Integer> second = () -> outboxDao.acquireLease(new DiagnosisEventLeaseCommand(
                insert.getId(), NOW, "worker-b", "token-b", NOW.plusSeconds(10)));
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Integer> results = executor.invokeAll(List.of(first, second)).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    }).toList();
            assertThat(results).containsExactlyInAnyOrder(0, 1);
        }

        DiagnosisEventOutbox winner = outboxDao.findById(insert.getId()).orElseThrow();
        assertThat(outboxDao.recoverExpired(NOW.plusSeconds(10))).isOne();
        assertThat(outboxDao.markDelivered(new DiagnosisEventTransitionCommand(
                insert.getId(), winner.leaseToken(), NOW.plusSeconds(11), null, null))).isZero();
        assertThat(outboxDao.acquireLease(new DiagnosisEventLeaseCommand(
                insert.getId(), NOW.plusSeconds(11), "worker-c", "token-c", NOW.plusMinutes(1)))).isOne();
        assertThat(outboxDao.markDead(new DiagnosisEventTransitionCommand(
                insert.getId(), "token-c", NOW.plusSeconds(12), null, "PERMANENT_ERROR"))).isOne();
        assertThat(outboxDao.findById(insert.getId()).orElseThrow().status()).isEqualTo(DiagnosisOutboxStatus.DEAD);
    }

    @Test
    void leaseServiceUsesBoundedPagesAndStopsAtAttemptLimit() {
        DiagnosisDeliveryPolicy policy = new DiagnosisDeliveryPolicy(1, Duration.ofDays(10),
                Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofMinutes(1), 2);
        for (int index = 0; index < 3; index++) {
            outboxDao.insert(command(UUID.randomUUID().toString(), "batch-" + UUID.randomUUID(),
                    1300 + index, 2300 + index));
        }
        DiagnosisEventLeaseService firstPass = leaseService(policy, NOW, "lease-1");
        List<DiagnosisEventOutbox> leased = firstPass.leaseReady("worker-1");
        assertThat(leased).hasSize(2);

        DiagnosisEventOutbox event = leased.getFirst();
        stateService.scheduleRetry(event, "TEMPORARY_ERROR", NOW.plusSeconds(2), NOW.plusSeconds(1));
        DiagnosisEventLeaseService secondPass = leaseService(policy, NOW.plusSeconds(2), "lease-2");
        assertThat(secondPass.leaseReady("worker-2")).noneMatch(item -> item.id().equals(event.id()));
        DiagnosisEventOutbox exhausted = outboxDao.findById(event.id()).orElseThrow();
        assertThat(exhausted.status()).isEqualTo(DiagnosisOutboxStatus.DEAD);
        assertThat(exhausted.lastErrorCode()).isEqualTo("MAX_ATTEMPTS_EXCEEDED");
        assertThat(auditDao.findByEventId(event.eventId())).extracting("action")
                .containsExactly("LEASED", "ATTEMPTED", "RETRY_SCHEDULED", "LEASED", "ATTEMPTED", "DEAD");
    }

    @Test
    void leaseServiceStopsEventsAtMaximumAge() {
        LocalDateTime readyAt = LocalDateTime.now().minusMinutes(1);
        DiagnosisEventOutboxInsert insert = commandAt(UUID.randomUUID().toString(),
                "age-" + UUID.randomUUID(), 1401, 2401, readyAt);
        outboxDao.insert(insert);
        DiagnosisEventOutbox stored = outboxDao.findById(insert.getId()).orElseThrow();
        LocalDateTime expiredAt = stored.createdAt().plusSeconds(1);
        DiagnosisDeliveryPolicy policy = new DiagnosisDeliveryPolicy(3, Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofMinutes(1), 10);

        assertThat(leaseService(policy, expiredAt, "age-token").leaseReady("worker-age")).isEmpty();
        DiagnosisEventOutbox exhausted = outboxDao.findById(insert.getId()).orElseThrow();
        assertThat(exhausted.status()).isEqualTo(DiagnosisOutboxStatus.DEAD);
        assertThat(exhausted.lastErrorCode()).isEqualTo("MAX_EVENT_AGE_EXCEEDED");
    }

    @Test
    void auditFailureRollsBackDeliveryStateMutation() {
        DiagnosisEventOutboxInsert insert = command(UUID.randomUUID().toString(),
                "rollback-" + UUID.randomUUID(), 1501, 2501);
        outboxDao.insert(insert);
        assertThat(outboxDao.acquireLease(new DiagnosisEventLeaseCommand(
                insert.getId(), NOW, "worker-r", "token-r", NOW.plusMinutes(1)))).isOne();
        DiagnosisEventOutbox leased = outboxDao.findById(insert.getId()).orElseThrow();
        doThrow(new IllegalStateException("forced audit failure")).when(auditDao).append(any());

        assertThatThrownBy(() -> stateService.markDelivered(leased, "ACCEPTED", NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);

        DiagnosisEventOutbox rolledBack = outboxDao.findById(insert.getId()).orElseThrow();
        assertThat(rolledBack.status()).isEqualTo(DiagnosisOutboxStatus.IN_FLIGHT);
        assertThat(rolledBack.deliveredAt()).isNull();
    }

    @Test
    void operatorReplayPreservesIdentityAndContentAndResetsDeadEvent() {
        String content = "{\"result\":\"immutable\"}";
        DiagnosisEventOutboxInsert insert = commandWithContent(content, 1601, 2601);
        outboxDao.insert(insert);
        assertThat(outboxDao.acquireLease(new DiagnosisEventLeaseCommand(
                insert.getId(), NOW, "worker-replay", "token-replay", NOW.plusMinutes(1)))).isOne();
        DiagnosisEventOutbox leased = outboxDao.findById(insert.getId()).orElseThrow();
        stateService.markDead(leased, "PERMANENT_REJECTION", "HTTP_400", NOW.plusSeconds(1));

        DiagnosisEventOutbox replayed = replayService.replay(
                insert.getEventId(), "operator-1", "downstream contract fixed");

        assertThat(replayed.status()).isEqualTo(DiagnosisOutboxStatus.PENDING);
        assertThat(replayed.attemptCount()).isZero();
        assertThat(replayed.eventId()).isEqualTo(insert.getEventId());
        assertThat(replayed.idempotencyKey()).isEqualTo(insert.getIdempotencyKey());
        assertThat(replayed.canonicalContent()).isEqualTo(content);
        assertThat(replayed.canonicalSha256()).isEqualTo(insert.getCanonicalSha256());
        assertThat(auditDao.findByEventId(insert.getEventId())).extracting("action")
                .containsExactly("DEAD", "OPERATOR_REPLAY");
    }

    @Test
    void replayAuditFailureRollsBackDeadState() {
        DiagnosisEventOutboxInsert insert = commandWithContent("{}", 1701, 2701);
        outboxDao.insert(insert);
        assertThat(outboxDao.acquireLease(new DiagnosisEventLeaseCommand(
                insert.getId(), NOW, "worker-replay", "token-replay", NOW.plusMinutes(1)))).isOne();
        DiagnosisEventOutbox leased = outboxDao.findById(insert.getId()).orElseThrow();
        stateService.markDead(leased, "PERMANENT_REJECTION", "HTTP_400", NOW.plusSeconds(1));
        doThrow(new IllegalStateException("forced audit failure")).when(auditDao).append(any());

        assertThatThrownBy(() -> replayService.replay(insert.getEventId(), "operator-1", "retry"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(outboxDao.findById(insert.getId()).orElseThrow().status())
                .isEqualTo(DiagnosisOutboxStatus.DEAD);
    }

    @Test
    void auditMapperIsAppendOnlyAndNonceSurvivesUntilExpiry() {
        String eventId = UUID.randomUUID().toString();
        auditDao.append(new DiagnosisEventAuditInsert(eventId, "investigation.completed",
                "CREATED", "SUCCESS", null, null, null, "corr-1"));
        auditDao.append(new DiagnosisEventAuditInsert(eventId, "investigation.completed",
                "LEASED", "SUCCESS", null, null, null, "corr-1"));
        assertThat(auditDao.findByEventId(eventId)).extracting("action").containsExactly("CREATED", "LEASED");
        assertThat(Arrays.stream(DiagnosisEventAuditDao.class.getDeclaredMethods()).map(method -> method.getName()))
                .containsExactlyInAnyOrder("append", "findByEventId");

        String nonce = "nonce-" + UUID.randomUUID();
        nonceDao.insert(new DiagnosisReplayNonceInsert(nonce, NOW.plusMinutes(5)));
        assertThat(nonceDao.findActive(nonce, NOW)).isPresent();
        assertThat(nonceDao.existsActive(nonce, NOW)).isTrue();
        assertThatThrownBy(() -> nonceDao.insert(new DiagnosisReplayNonceInsert(nonce, NOW.plusMinutes(5))))
                .isInstanceOf(RuntimeException.class);
        assertThat(nonceDao.existsActive(nonce, NOW.plusMinutes(6))).isFalse();
        assertThat(nonceDao.deleteExpired(NOW.plusMinutes(6))).isOne();
    }

    private void assertDuplicate(DiagnosisEventOutboxInsert duplicate) {
        assertThatThrownBy(() -> outboxDao.insert(duplicate)).isInstanceOf(RuntimeException.class);
    }

    private DiagnosisEventOutboxInsert command(String eventId, String idempotencyKey,
                                                long investigationId, long runId) {
        return commandAt(eventId, idempotencyKey, investigationId, runId, NOW);
    }

    private DiagnosisEventOutboxInsert commandAt(String eventId, String idempotencyKey,
                                                  long investigationId, long runId,
                                                  LocalDateTime nextAttemptAt) {
        return new DiagnosisEventOutboxInsert(eventId, idempotencyKey, investigationId, runId,
                "investigation.completed", 1, "1.0", "{\"eventId\":\"" + eventId + "\"}",
                "a".repeat(64), nextAttemptAt);
    }

    private DiagnosisEventOutboxInsert commandWithContent(String content, long investigationId, long runId) {
        String eventId = UUID.randomUUID().toString();
        return new DiagnosisEventOutboxInsert(eventId, "replay-" + UUID.randomUUID(), investigationId, runId,
                "investigation.completed", 1, "1.0", content, sha256(content), NOW);
    }

    private String sha256(String content) {
        try {
            byte[] value = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private DiagnosisEventLeaseService leaseService(DiagnosisDeliveryPolicy policy,
                                                     LocalDateTime now, String token) {
        Clock clock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new DiagnosisEventLeaseService(outboxDao, stateService, policy, clock, () -> token);
    }
}
