package com.dpom.agent.alarm.domain;

/**
 * 通知渠道：告警事件通知的发送通道。
 */
public enum NotificationChannel {

    /** 邮件。 */
    EMAIL,
    /** IM webhook。 */
    IM_WEBHOOK
}
