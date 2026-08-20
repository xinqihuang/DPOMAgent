package com.dpom.agent.alarm.persistence;

import com.dpom.agent.common.alarm.AlarmIncidentStatus;
import com.dpom.agent.common.alarm.SeverityLevel;

import java.time.LocalDateTime;

/**
 * 告警事件分页查询参数。
 *
 * @param status     状态过滤（可为空）
 * @param severity   严重度过滤（可为空）
 * @param serviceCode 服务过滤（可为空）
 * @param fromTime   起始时间过滤（可为空，按 started_at）
 * @param toTime     结束时间过滤（可为空）
 * @param cursorId   游标 id（可为空表示首页）
 * @param limit      每页大小
 */
public record AlarmIncidentQuery(AlarmIncidentStatus status, SeverityLevel severity, String serviceCode,
                                 LocalDateTime fromTime, LocalDateTime toTime, Long cursorId, int limit) {
}
