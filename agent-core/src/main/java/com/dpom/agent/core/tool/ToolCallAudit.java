package com.dpom.agent.core.tool;

import java.time.LocalDateTime;

/**
 * 工具调用审计：仅追加的工具调用记录。
 *
 * @param id                 主键
 * @param investigationId    关联调查 id
 * @param runId              关联 Run id（可为空）
 * @param toolName           工具名
 * @param toolInput          工具入参（JSON，可为空）
 * @param toolOutputSummary  工具输出摘要（可为空）
 * @param durationMs         耗时毫秒（可为空）
 * @param success            是否成功（可为空）
 * @param errorMessage       错误信息（可为空）
 * @param createdAt          创建时间
 */
public record ToolCallAudit(Long id, Long investigationId, Long runId, String toolName, String toolInput,
                            String toolOutputSummary, Long durationMs, Boolean success, String errorMessage,
                            LocalDateTime createdAt) {
}
