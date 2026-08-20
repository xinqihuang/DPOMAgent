package com.dpom.agent.common.alarm;

/**
 * 处置工件生成结果。
 *
 * @param artifactId     工件 id
 * @param approvalStatus 审批状态（期望 {@code REQUIRES_APPROVAL}）
 */
public record HandlingArtifactResult(Long artifactId, String approvalStatus) {
}
