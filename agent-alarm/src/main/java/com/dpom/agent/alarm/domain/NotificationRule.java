package com.dpom.agent.alarm.domain;

import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.SeverityLevel;

import java.time.LocalDateTime;

/**
 * 通知规则：按事件属性匹配并分派到一个或多个通知渠道。
 *
 * @param id              主键
 * @param name            规则名称
 * @param sourceFilter    来源服务过滤（可为空表示不限）
 * @param serviceCodeFilter 服务编码过滤（可为空）
 * @param resourceFilter  资源过滤（可为空）
 * @param severityFilter  严重度过滤（可为空）
 * @param tagFilter       标签过滤（可为空）
 * @param channels        渠道配置（JSON 数组）
 * @param enabled         是否启用
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 */
public record NotificationRule(Long id, String name, AlarmSource sourceFilter, String serviceCodeFilter,
                               String resourceFilter, SeverityLevel severityFilter, String tagFilter,
                               String channels, boolean enabled, LocalDateTime createdAt,
                               LocalDateTime updatedAt) {
}
