package com.dpom.agent.core.investigation;

import java.time.LocalDateTime;

/**
 * 调查步骤：仅追加的原子步骤记录，不做更新或删除。
 *
 * @param id              主键
 * @param investigationId 关联调查 id
 * @param runId           关联 Run id（可为空）
 * @param stepOrder       步骤序号
 * @param stepType        步骤类型
 * @param summary         步骤摘要
 * @param payloadJson     步骤负载（JSON，可为空）
 * @param createdAt       创建时间
 */
public record InvestigationStep(Long id, Long investigationId, Long runId, int stepOrder, String stepType,
                                String summary, String payloadJson, LocalDateTime createdAt) {
}
