package com.dpom.agent.core.diagnosisevent;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 使用事件标识和尝试次数生成可重现的有界重试计划。
 */
public final class DiagnosisRetryPolicy {

    private final DiagnosisDeliveryPolicy policy;

    /** 创建重试策略。 */
    public DiagnosisRetryPolicy(DiagnosisDeliveryPolicy policy) {
        this.policy = policy;
    }

    /** 判断尝试次数是否耗尽。 */
    public boolean attemptsExhausted(DiagnosisEventOutbox event) {
        return event.attemptCount() >= policy.maxAttempts();
    }

    /** 判断事件是否超过最大年龄。 */
    public boolean ageExhausted(DiagnosisEventOutbox event, LocalDateTime now) {
        return !event.createdAt().plus(policy.maxEventAge()).isAfter(now);
    }

    /** 计算下一次尝试时间。 */
    public LocalDateTime nextAttemptAt(String eventId, int attemptCount, LocalDateTime now) {
        long baseMillis = policy.baseDelay().toMillis();
        int shift = Math.max(0, Math.min(30, attemptCount - 1));
        long exponential = multiplyCapped(baseMillis, 1L << shift, policy.maxDelay().toMillis());
        long permille = 500L + Math.floorMod((eventId + ':' + attemptCount).hashCode(), 501);
        long jittered = Math.max(1L, exponential * permille / 1000L);
        return now.plus(Duration.ofMillis(Math.min(jittered, policy.maxDelay().toMillis())));
    }

    private long multiplyCapped(long left, long right, long cap) {
        if (left > cap / right) {
            return cap;
        }
        return Math.min(left * right, cap);
    }
}
