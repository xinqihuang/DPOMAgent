package com.dpom.agent.core.diagnosisevent;

import com.dpom.agent.core.persistence.DiagnosisEventOutboxDao;
import com.dpom.agent.core.persistence.command.DiagnosisEventLeaseCommand;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 以乐观 CAS 获取有界批次的投递租约。
 */
public final class DiagnosisEventLeaseService {

    private final DiagnosisEventOutboxDao outboxDao;
    private final DiagnosisEventStateService stateService;
    private final DiagnosisDeliveryPolicy policy;
    private final DiagnosisRetryPolicy retryPolicy;
    private final Clock clock;
    private final LeaseTokenSource tokenSource;

    /** 创建租约服务。 */
    public DiagnosisEventLeaseService(DiagnosisEventOutboxDao outboxDao, DiagnosisEventStateService stateService,
                                      DiagnosisDeliveryPolicy policy, Clock clock, LeaseTokenSource tokenSource) {
        this.outboxDao = outboxDao;
        this.stateService = stateService;
        this.policy = policy;
        this.retryPolicy = new DiagnosisRetryPolicy(policy);
        this.clock = clock;
        this.tokenSource = tokenSource;
    }

    /**
     * 恢复过期租约并竞争一批可投递事件。
     *
     * @param workerId 有界工作者标识
     * @return 当前工作者拥有的事件
     */
    public List<DiagnosisEventOutbox> leaseReady(String workerId) {
        if (workerId == null || !workerId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("INVALID_WORKER_ID");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        stateService.recoverExpired(now, policy.batchSize());
        List<DiagnosisEventOutbox> leased = new ArrayList<>();
        for (Long id : outboxDao.findReadyIds(now, policy.batchSize())) {
            acquireOne(id, workerId, now).ifPresent(leased::add);
        }
        return List.copyOf(leased);
    }

    private java.util.Optional<DiagnosisEventOutbox> acquireOne(long id, String workerId, LocalDateTime now) {
        DiagnosisEventOutbox candidate = outboxDao.findById(id).orElseThrow();
        String token = tokenSource.nextToken();
        boolean acquired = stateService.acquireLease(candidate, new DiagnosisEventLeaseCommand(id, now, workerId, token,
                now.plus(policy.leaseDuration())));
        if (!acquired) {
            return java.util.Optional.empty();
        }
        DiagnosisEventOutbox leased = outboxDao.findById(id).orElseThrow();
        String exhausted = exhaustedCode(candidate, now);
        if (exhausted != null) {
            stateService.markDead(leased, "RETRY_EXHAUSTED", exhausted, now);
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(leased);
    }

    private String exhaustedCode(DiagnosisEventOutbox event, LocalDateTime now) {
        if (retryPolicy.attemptsExhausted(event)) {
            return "MAX_ATTEMPTS_EXCEEDED";
        }
        return retryPolicy.ageExhausted(event, now) ? "MAX_EVENT_AGE_EXCEEDED" : null;
    }
}
