package com.dpom.agent.web.dto;

/**
 * 代码锚点审计视图 DTO。
 */
public record CodeAnchorResponse(String type, String value, String sourceEvidenceId, double confidence,
        String ruleVersion) { }
