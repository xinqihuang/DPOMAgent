package com.dpom.agent.alarm.query;

import com.dpom.agent.alarm.domain.Alarm;

import java.util.List;

/**
 * 告警分页结果。
 *
 * @param items      当前页告警
 * @param nextCursor 下一页游标（无更多数据时为空）
 */
public record AlarmPage(List<Alarm> items, Long nextCursor) {
}
