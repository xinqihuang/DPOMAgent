package com.dpom.agent.alarm.governance;

/**
 * 去重决策：合并已存在告警或新建。
 *
 * @param merge          是否合并
 * @param existingAlarmId 已存在告警 id（合并时非空）
 */
public record DedupDecision(boolean merge, Long existingAlarmId) {

    /**
     * 构造合并决策。
     *
     * @param existingAlarmId 已存在告警 id
     * @return 合并决策
     */
    public static DedupDecision merge(Long existingAlarmId) {
        return new DedupDecision(true, existingAlarmId);
    }

    /**
     * 构造新建决策。
     *
     * @return 新建决策
     */
    public static DedupDecision newAlarm() {
        return new DedupDecision(false, null);
    }
}
