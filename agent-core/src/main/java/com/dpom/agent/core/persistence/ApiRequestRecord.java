package com.dpom.agent.core.persistence;

import java.time.LocalDateTime;

/**
 * 调查 API 幂等/执行记录。
 *
 * @param id               主键
 * @param idempotencyKey   幂等键
 * @param payloadHash      payload 哈希
 * @param investigationId  调查 id
 * @param status           状态（SUBMITTED/RUNNING/COMPLETED/INCONCLUSIVE/FAILED/REJECTED）
 * @param startedAt        开始时间
 * @param completedAt      完成时间
 * @param lastErrorCode    最近错误码
 * @param createdAt        创建时间
 */
public record ApiRequestRecord(Long id, String idempotencyKey, String payloadHash, Long investigationId,
                               String status, LocalDateTime startedAt, LocalDateTime completedAt,
                               String lastErrorCode, LocalDateTime createdAt) {
}
