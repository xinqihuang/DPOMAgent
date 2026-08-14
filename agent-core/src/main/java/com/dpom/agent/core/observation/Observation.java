package com.dpom.agent.core.observation;

import java.time.LocalDateTime;

/**
 * 观察：一条证据，来自运行时、代码图或工作区，可支持或反驳若干假设。
 *
 * @param id                      主键
 * @param investigationId         关联调查 id
 * @param runId                   关联 Run id（可为空）
 * @param source                  证据来源（如 runtime/codegraph/workspace/script）
 * @param artifactRef             证据工件引用（可为空）
 * @param location                代码位置或日志位置（可为空）
 * @param supportsHypothesisIds   支持的假设 id 列表（逗号分隔，可为空）
 * @param contradictsHypothesisIds 反驳的假设 id 列表（逗号分隔，可为空）
 * @param summary                 证据摘要
 * @param payloadJson             证据负载（JSON，可为空）
 * @param createdAt               创建时间
 */
public record Observation(Long id, Long investigationId, Long runId, String source, String artifactRef,
                          String location, String supportsHypothesisIds, String contradictsHypothesisIds,
                          String summary, String payloadJson, LocalDateTime createdAt) {
}
