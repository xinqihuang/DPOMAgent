package com.dpom.agent.alarm.persistence;

import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.AlarmStatus;
import com.dpom.agent.common.alarm.SeverityLevel;

import java.time.LocalDateTime;

/**
 * 告警分页查询参数：各过滤字段可为空表示不限；{@code cursorId} 为空表示首页。
 *
 * <p>游标采用 keyset 分页（{@code id < cursorId}，按 id 倒序）。</p>
 *
 * @param source      来源过滤（可为空）
 * @param resourceId  资源过滤（可为空）
 * @param serviceCode 服务过滤（可为空）
 * @param severity    严重度过滤（可为空）
 * @param status      状态过滤（可为空）
 * @param fromTime    起始时间过滤（可为空，按 last_occurred_at）
 * @param toTime      结束时间过滤（可为空）
 * @param cursorId    游标 id（可为空表示首页）
 * @param limit       每页大小
 */
public record AlarmQuery(AlarmSource source, String resourceId, String serviceCode, SeverityLevel severity,
                         AlarmStatus status, LocalDateTime fromTime, LocalDateTime toTime, Long cursorId,
                         int limit) {
}
