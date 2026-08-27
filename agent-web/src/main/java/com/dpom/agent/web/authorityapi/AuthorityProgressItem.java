package com.dpom.agent.web.authorityapi;

import java.time.LocalDateTime;

/** 进度页和 SSE 共用的低基数安全事件。 */
public record AuthorityProgressItem(long sequence, long aggregateVersion, String kind,
                                    String entityId, String reasonCode, LocalDateTime occurredAt) {
}

