package com.dpom.agent.core.persistence.authority;

import java.time.LocalDateTime;

/** MyBatis 安全 ToolUse 记录，不包含原始参数、响应或凭据。 */
public record AuthorityToolUseRow(String toolUseId, String investigationId, String runId,
                                  String toolName, String contractVersion, String argumentSha256,
                                  String argumentNamesJson, int argumentSizeBytes, String targetScope,
                                  String correlationId, String status, String reasonCode,
                                  String evidenceReferencesJson, LocalDateTime occurredAt) {
}
