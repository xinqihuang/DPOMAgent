package com.dpom.agent.web.dto;

/**
 * 源码证据审计视图 DTO（绑定 commit、已验证状态）。
 */
public record CodeEvidenceResponse(String evidenceId, String anchorValue, String symbol, String filePath,
        Integer lineNumber, String commit, String excerpt, String status) { }
