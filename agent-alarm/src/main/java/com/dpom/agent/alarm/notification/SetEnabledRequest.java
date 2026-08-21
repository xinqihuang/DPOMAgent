package com.dpom.agent.alarm.notification;

/**
 * 启停通知规则请求。
 *
 * @param enabled 是否启用
 */
public record SetEnabledRequest(boolean enabled) {
}
