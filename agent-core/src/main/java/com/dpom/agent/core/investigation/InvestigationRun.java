package com.dpom.agent.core.investigation;

import java.time.LocalDateTime;

/**
 * 调查运行：一次可恢复的自动调查执行，记录 model/prompt/toolset 版本。
 *
 * @param id              主键
 * @param investigationId 关联调查 id
 * @param modelVersion    模型版本
 * @param promptVersion   Prompt 版本
 * @param toolsetVersion  工具集版本
 * @param startedAt       开始时间
 * @param endedAt         结束时间（可为空）
 */
public record InvestigationRun(Long id, Long investigationId, String modelVersion, String promptVersion,
                               String toolsetVersion, LocalDateTime startedAt, LocalDateTime endedAt) {
}
