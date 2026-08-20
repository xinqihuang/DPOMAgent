package com.dpom.agent.common.alarm;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警事件触发诊断请求：agent-alarm 经此请求 agent-core 启动 Investigation。
 *
 * @param incidentId      告警事件 id
 * @param serviceCode     服务编码
 * @param environment     环境标识
 * @param severity        聚合严重度
 * @param summary         事件摘要
 * @param memberAlarmIds  成员告警 id 列表
 * @param occurredAt      事件发生时间
 */
public record AlarmIncidentTriggerRequest(Long incidentId, String serviceCode, String environment,
                                          SeverityLevel severity, String summary,
                                          List<Long> memberAlarmIds, LocalDateTime occurredAt) {
}
