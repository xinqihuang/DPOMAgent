package com.dpom.agent.alarm.ingestion;

/**
 * 告警接入结果。
 *
 * @param accepted       是否已接入
 * @param alarmId        告警 id（未接入时为空）
 * @param rejectionReason 拒绝原因（已接入时为空）
 */
public record AlarmIngestionResult(boolean accepted, Long alarmId, String rejectionReason) {

    /**
     * 构造已接入结果。
     *
     * @param alarmId 告警 id
     * @return 已接入结果
     */
    public static AlarmIngestionResult accepted(Long alarmId) {
        return new AlarmIngestionResult(true, alarmId, null);
    }

    /**
     * 构造已拒绝结果。
     *
     * @param reason 拒绝原因
     * @return 已拒绝结果
     */
    public static AlarmIngestionResult rejected(String reason) {
        return new AlarmIngestionResult(false, null, reason);
    }
}
