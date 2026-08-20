package com.dpom.agent.alarm.query;

import com.dpom.agent.alarm.domain.AlarmIncident;

import java.util.List;

/**
 * 告警事件分页结果。
 *
 * @param items      当前页事件
 * @param nextCursor 下一页游标（无更多数据时为空）
 */
public record AlarmIncidentPage(List<AlarmIncident> items, Long nextCursor) {
}
