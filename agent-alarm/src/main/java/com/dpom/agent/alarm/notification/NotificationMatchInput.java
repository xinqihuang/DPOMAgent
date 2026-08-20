package com.dpom.agent.alarm.notification;

import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.SeverityLevel;

import java.util.Map;

/**
 * 通知规则匹配输入：从告警事件及其成员告警提取的待匹配属性。
 *
 * @param source      来源服务（可为空）
 * @param serviceCode 服务编码（可为空）
 * @param resourceId  资源标识（可为空）
 * @param severity    严重度（可为空）
 * @param tags        标签（可为空）
 */
public record NotificationMatchInput(AlarmSource source, String serviceCode, String resourceId,
                                     SeverityLevel severity, Map<String, String> tags) {
}
