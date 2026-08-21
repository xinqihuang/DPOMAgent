package com.dpom.agent.alarm.correlation;

import java.util.List;

/**
 * 关联诊断请求：待关联的告警 id 列表。
 *
 * @param alarmIds 告警 id 列表
 */
public record CorrelateRequest(List<Long> alarmIds) {
}
