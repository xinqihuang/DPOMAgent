package com.dpom.agent.alarm.notification;

import com.dpom.agent.alarm.domain.NotificationChannel;

/**
 * 通知渠道发送器：各渠道实现统一抽象，由 {@link NotificationDispatchService} 按渠道分派。
 */
public interface NotificationSender {

    /**
     * 返回该发送器支持的渠道。
     *
     * @return 渠道
     */
    NotificationChannel channel();

    /**
     * 发送通知。
     *
     * @param message 通知消息
     * @return 发送结果
     */
    SendOutcome send(NotificationMessage message);
}
