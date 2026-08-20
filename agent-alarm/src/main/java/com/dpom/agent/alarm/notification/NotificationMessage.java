package com.dpom.agent.alarm.notification;

/**
 * 通知消息：待发送的事件通知内容与目标。
 *
 * @param incidentId 事件 id
 * @param subject    主题
 * @param body       正文
 * @param target     渠道目标
 */
public record NotificationMessage(long incidentId, String subject, String body, ChannelTarget target) {
}
