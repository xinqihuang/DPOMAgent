package com.dpom.agent.web.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 幂等冲突（同 key 不同 payload）：携带已存在调查 id 供稳定错误响应使用。
 */
public class InvestigationConflictException extends ResponseStatusException {

    private final long investigationId;

    public InvestigationConflictException(long investigationId) {
        super(HttpStatus.CONFLICT, "idempotencyKey payload mismatch");
        this.investigationId = investigationId;
    }

    public long investigationId() {
        return investigationId;
    }
}
