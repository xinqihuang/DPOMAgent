package com.dpom.agent.core.incident;

import java.time.LocalDateTime;

/**
 * 事件：一次故障调查的入口，绑定服务、环境、发布版本与提交。
 *
 * @param id             主键
 * @param serviceCode    服务编码
 * @param environment    环境标识（如 dev/staging/prod）
 * @param releaseVersion 发布版本
 * @param commitSha      关联提交（用于解析代码快照）
 * @param symptom        症状描述
 * @param createdAt      创建时间
 */
public record Incident(Long id, String serviceCode, String environment, String releaseVersion,
                       String commitSha, String symptom, LocalDateTime createdAt) {
}
