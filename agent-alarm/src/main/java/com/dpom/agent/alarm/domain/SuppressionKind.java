package com.dpom.agent.alarm.domain;

/**
 * 抑制类型：按条件暂停通知（SUPPRESSION）或按时间区间静默（SILENCE）。
 */
public enum SuppressionKind {

    /** 条件抑制。 */
    SUPPRESSION,
    /** 时间区间静默。 */
    SILENCE
}
