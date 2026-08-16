package com.dpom.agent.core.handoff;

import java.time.LocalDateTime;

/**
 * 交接上传记录：独立于升级判定的批准/上传审计记录，批准绑定具体 packageId。
 *
 * @param id                  主键
 * @param investigationId     调查 id
 * @param packageId           包标识（唯一）
 * @param objectKey           OBS 对象名（上传后写入，可为空）
 * @param schemaVersion       schema 版本
 * @param checksum            ZIP 校验和（可为空）
 * @param sizeBytes           包字节大小
 * @param approvalStatus      批准状态
 * @param approvedAt          批准时间（可为空）
 * @param approverRef         外部审批引用（可为空）
 * @param approvalReason      审批理由（可为空）
 * @param approvalExpiresAt   审批过期时间（可为空）
 * @param uploadedAt          上传时间（可为空）
 * @param createdAt           创建时间
 */
public record HandoffUpload(Long id, Long investigationId, String packageId, String objectKey, int schemaVersion,
                            String checksum, long sizeBytes, UploadApprovalStatus approvalStatus,
                            LocalDateTime approvedAt, String approverRef, String approvalReason,
                            LocalDateTime approvalExpiresAt, LocalDateTime uploadedAt, LocalDateTime createdAt) {
}
