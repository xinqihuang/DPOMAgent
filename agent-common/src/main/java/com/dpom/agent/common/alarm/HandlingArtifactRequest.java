package com.dpom.agent.common.alarm;

/**
 * 处置工件生成请求：agent-alarm 经此委托 agent-core 生成带 {@code REQUIRES_APPROVAL} 的脚本工件。
 *
 * <p>本请求不携带任何凭据（AK/SK/token/cookie），仅包含工件内容与元数据。</p>
 *
 * @param investigationId 关联调查 id
 * @param artifactType    工件类型（如 MITIGATION、READ_ONLY_DIAGNOSTIC）
 * @param language        脚本语言（shell/python/sql）
 * @param purpose         用途
 * @param riskLevel       风险等级
 * @param preconditions   前置条件（可为空）
 * @param verification    验证方式（可为空）
 * @param rollback        回滚方案（可为空）
 * @param content         脚本内容
 */
public record HandlingArtifactRequest(Long investigationId, String artifactType, String language, String purpose,
                                      String riskLevel, String preconditions, String verification,
                                      String rollback, String content) {
}
