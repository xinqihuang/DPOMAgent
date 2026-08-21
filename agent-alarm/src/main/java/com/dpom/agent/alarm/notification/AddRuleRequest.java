package com.dpom.agent.alarm.notification;

import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.SeverityLevel;

/**
 * 新增通知规则请求。
 *
 * @param name              规则名称
 * @param sourceFilter      来源过滤（可空表示不限）
 * @param serviceCodeFilter 服务编码过滤（可空）
 * @param resourceFilter    资源过滤（可空）
 * @param severityFilter    严重度过滤（可空）
 * @param channels          渠道配置（JSON 数组字符串）
 * @param enabled           是否启用
 */
public record AddRuleRequest(String name, AlarmSource sourceFilter, String serviceCodeFilter,
                             String resourceFilter, SeverityLevel severityFilter, String channels,
                             boolean enabled) {
}
