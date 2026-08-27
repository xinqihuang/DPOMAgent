package com.dpom.agent.common.diagnosisevent;

/**
 * 下游对 Diagnosis Event 的稳定处理结果。
 */
public enum DeliveryOutcome {
    /** 已接受。 */
    ACCEPTED,
    /** 已存在内容等价的幂等事件。 */
    EQUIVALENT_DUPLICATE,
    /** 可重试失败。 */
    RETRYABLE_FAILURE,
    /** 永久拒绝。 */
    PERMANENT_REJECTION,
    /** 相同幂等键对应不同内容。 */
    IDEMPOTENCY_CONFLICT
}
