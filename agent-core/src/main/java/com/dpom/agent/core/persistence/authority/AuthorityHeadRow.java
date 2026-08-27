package com.dpom.agent.core.persistence.authority;

import java.time.LocalDateTime;

/** MyBatis 当前权威头记录。 */
public record AuthorityHeadRow(String investigationId, String incidentId, long aggregateVersion,
                               String status, String currentRunId, int stepsUsed, int toolCallsUsed,
                               int noProgressRounds, String snapshotJson, String snapshotSha256,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
}

