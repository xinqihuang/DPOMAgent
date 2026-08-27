package com.dpom.agent.web.authorityapi;

import java.util.List;

/** 有界、游标式 Investigation 进度页。 */
public record AuthorityProgressPage(String investigationId, long aggregateVersion, String status,
                                    long requestedAfter, long nextAfter, boolean hasMore,
                                    List<AuthorityProgressItem> items) {

    /** 冻结返回集合。 */
    public AuthorityProgressPage {
        items = List.copyOf(items);
    }
}

