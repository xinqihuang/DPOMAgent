package com.dpom.agent.core.conclusion;

import java.time.LocalDateTime;

/**
 * 结论：调查最终综合结果。
 *
 * @param id                  主键
 * @param investigationId     关联调查 id
 * @param resultType          结论类型
 * @param rootCauseId         稳定根因标识（如 AssetRepository.insert，可为空）
 * @param rootCause           根因自然语言描述
 * @param evidenceIds         支撑证据 id 列表（逗号分隔，可为空）
 * @param unresolvedQuestions 未决问题（可为空）
 * @param summary             结论摘要
 * @param createdAt           创建时间
 */
public record Conclusion(Long id, Long investigationId, String resultType, String rootCauseId, String rootCause,
                         String evidenceIds, String unresolvedQuestions, String summary, LocalDateTime createdAt) {
}
