package com.dpom.agent.common.alarm;

/**
 * 告警状态：单条告警的触发与恢复状态。
 */
public enum AlarmStatus {

    /** 触发中。 */
    FIRING,
    /** 已恢复。 */
    RESOLVED
}
