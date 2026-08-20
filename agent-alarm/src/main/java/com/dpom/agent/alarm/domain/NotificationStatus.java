package com.dpom.agent.alarm.domain;

/**
 * 通知发送状态。
 */
public enum NotificationStatus {

    /** 已发送。 */
    SENT,
    /** 发送失败。 */
    FAILED,
    /** 跳过（静默/抑制/无匹配）。 */
    SKIPPED
}
