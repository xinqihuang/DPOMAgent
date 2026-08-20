package com.dpom.agent.alarm.correlation;

import com.dpom.agent.alarm.domain.AlarmIncident;

import java.util.List;

/**
 * 关联产出：候选事件及其成员告警 id 列表（事件 id 为空，待持久化回填）。
 *
 * @param incident       候选事件（id 为空）
 * @param memberAlarmIds 成员告警 id 列表
 */
public record CorrelatedIncident(AlarmIncident incident, List<Long> memberAlarmIds) {
}
