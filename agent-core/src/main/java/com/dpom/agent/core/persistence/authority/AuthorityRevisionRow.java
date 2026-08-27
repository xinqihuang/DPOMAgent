package com.dpom.agent.core.persistence.authority;

import java.time.LocalDateTime;

/** MyBatis 不可变权威版本记录。 */
public record AuthorityRevisionRow(String investigationId, long aggregateVersion, String status,
                                   String snapshotJson, String snapshotSha256,
                                   LocalDateTime recordedAt) {
}

