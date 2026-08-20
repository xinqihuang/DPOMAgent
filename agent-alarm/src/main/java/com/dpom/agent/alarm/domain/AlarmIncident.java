package com.dpom.agent.alarm.domain;

import com.dpom.agent.common.alarm.AlarmIncidentStatus;
import com.dpom.agent.common.alarm.SeverityLevel;

import java.time.LocalDateTime;

/**
 * 告警事件：告警经关联聚合产出的事件，可触发诊断调查。
 *
 * @param id                     主键
 * @param status                 生命周期状态
 * @param severity               聚合严重度
 * @param serviceCode            服务编码（可为空）
 * @param environment            环境标识（可为空）
 * @param correlationBasis       关联依据
 * @param summary                事件摘要（可为空）
 * @param startedAt              起始时间
 * @param endedAt                结束时间（可为空）
 * @param escalationCandidate    是否升级候选
 * @param escalationEvaluatedAt  升级评估时间（可为空）
 * @param assignee               处理人（可为空）
 * @param acknowledgedAt         认领时间（可为空）
 * @param resolvedAt             闭环时间（可为空）
 * @param createdAt              创建时间
 * @param updatedAt              更新时间
 */
public record AlarmIncident(Long id, AlarmIncidentStatus status, SeverityLevel severity, String serviceCode,
                            String environment, String correlationBasis, String summary,
                            LocalDateTime startedAt, LocalDateTime endedAt, boolean escalationCandidate,
                            LocalDateTime escalationEvaluatedAt, String assignee,
                            LocalDateTime acknowledgedAt, LocalDateTime resolvedAt,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
}
