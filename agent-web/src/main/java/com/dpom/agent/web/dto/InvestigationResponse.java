package com.dpom.agent.web.dto;

import java.time.LocalDateTime;

/**
 * 调查摘要与状态响应。
 *
 * @param investigationId 调查 id
 * @param status          状态
 * @param serviceCode     服务编码
 * @param symptom         症状
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 */
public record InvestigationResponse(long investigationId, String status, String serviceCode, String symptom,
                                    LocalDateTime createdAt, LocalDateTime updatedAt) {
}
