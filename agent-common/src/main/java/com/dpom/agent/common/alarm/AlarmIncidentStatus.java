package com.dpom.agent.common.alarm;

/**
 * 告警事件生命周期状态：关联聚合产出的 AlarmIncident 的状态。
 */
public enum AlarmIncidentStatus {

    /** 新建未认领。 */
    OPEN,
    /** 已认领。 */
    ACKNOWLEDGED,
    /** 已闭环。 */
    RESOLVED
}
