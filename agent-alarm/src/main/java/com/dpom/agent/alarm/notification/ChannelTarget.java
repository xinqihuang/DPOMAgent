package com.dpom.agent.alarm.notification;

import com.dpom.agent.alarm.domain.NotificationChannel;

/**
 * 通知渠道目标：渠道类型与接收方（邮箱地址或 webhook URL）。
 *
 * @param channel  渠道
 * @param recipient 接收方
 */
public record ChannelTarget(NotificationChannel channel, String recipient) {
}
