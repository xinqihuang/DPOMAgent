package com.dpom.agent.common.alarm;

/**
 * 告警事件触发诊断结果。
 *
 * @param triggered      是否已触发诊断
 * @param investigationId 关联调查 id（未触发时为空）
 * @param skipReason     未触发原因（已触发时为空）
 */
public record AlarmIncidentTriggerResult(boolean triggered, Long investigationId, String skipReason) {

    /**
     * 构造跳过结果。
     *
     * @param skipReason 跳过原因
     * @return 未触发结果
     */
    public static AlarmIncidentTriggerResult skipped(String skipReason) {
        return new AlarmIncidentTriggerResult(false, null, skipReason);
    }

    /**
     * 构造已触发结果。
     *
     * @param investigationId 调查 id
     * @return 已触发结果
     */
    public static AlarmIncidentTriggerResult triggered(Long investigationId) {
        return new AlarmIncidentTriggerResult(true, investigationId, null);
    }
}
