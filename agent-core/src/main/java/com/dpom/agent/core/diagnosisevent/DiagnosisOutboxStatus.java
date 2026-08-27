package com.dpom.agent.core.diagnosisevent;

/**
 * Diagnosis Event 发件箱状态。
 */
public enum DiagnosisOutboxStatus {
    /** 等待投递。 */ PENDING,
    /** 已获取投递租约。 */ IN_FLIGHT,
    /** 已成功投递。 */ DELIVERED,
    /** 已终止自动重试。 */ DEAD
}
