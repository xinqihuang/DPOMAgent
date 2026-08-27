package com.dpom.agent.core.investigation;

/**
 * 调查终态持久化命令。
 *
 * @param investigationId 调查标识
 * @param runId            当前运行标识，可为空
 * @param terminalStatus   目标终态
 * @param resultType       结论类型
 * @param rootCauseId      根因标识
 * @param rootCause        根因描述
 * @param summary          结论摘要
 * @param evidenceIds      证据标识列表
 */
public record InvestigationTerminalizationCommand(long investigationId, Long runId,
                                                   InvestigationStatus terminalStatus, String resultType,
                                                   String rootCauseId, String rootCause, String summary,
                                                   String evidenceIds) {
}
