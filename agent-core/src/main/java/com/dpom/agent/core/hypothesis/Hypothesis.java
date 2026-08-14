package com.dpom.agent.core.hypothesis;

import java.time.LocalDateTime;

/**
 * 假设：对故障根因的一个候选解释。
 *
 * @param id              主键
 * @param investigationId 关联调查 id
 * @param parentId        父假设 id（可为空）
 * @param description     假设描述
 * @param status          假设状态
 * @param missingChecks   尚缺的验证项（可为空）
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 */
public record Hypothesis(Long id, Long investigationId, Long parentId, String description,
                         HypothesisStatus status, String missingChecks,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
}
