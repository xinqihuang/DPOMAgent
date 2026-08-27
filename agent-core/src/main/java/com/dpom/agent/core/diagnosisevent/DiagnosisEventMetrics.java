package com.dpom.agent.core.diagnosisevent;

/**
 * Diagnosis Event 低基数指标边界。
 */
@FunctionalInterface
public interface DiagnosisEventMetrics {

    /** 记录一个有界状态结果。 */
    void record(String state, String result, String errorCode);
}
