package com.dpom.agent.core.investigation;

import java.time.LocalDateTime;

/**
 * 调查：一次 Incident 下的调查会话，包含状态、当前 Run 与预算上限。
 *
 * @param id                  主键
 * @param incidentId          关联事件 id
 * @param status              当前状态
 * @param currentRunId        当前 Run id（可为空）
 * @param maxSteps            最大步数预算
 * @param maxToolCalls        最大工具调用预算
 * @param maxDurationSeconds  最大时长预算（秒）
 * @param maxNoProgressRounds 最大无进展轮数预算
 * @param createdAt           创建时间
 * @param updatedAt           更新时间
 */
public record Investigation(Long id, Long incidentId, InvestigationStatus status, Long currentRunId,
                            int maxSteps, int maxToolCalls, int maxDurationSeconds, int maxNoProgressRounds,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
}
