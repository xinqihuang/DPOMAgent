package com.dpom.agent.web.dto;

import java.util.List;
import java.util.Map;

/**
 * 日志证据审计视图 DTO（已聚合、脱敏；含 provenance 与版本）。
 */
public record LogEvidenceResponse(String evidenceId, int clusterId, String template, int count, String firstSeen,
        String lastSeen, Map<String, Integer> severityDistribution, List<String> representativeSamples,
        Map<String, List<String>> parameterDistribution, boolean truncated, String service, String environment,
        String release, String commit, String timeRange, List<String> traceIds, String minerVersion,
        ProvenanceResponse provenance) {

    /** 证据来源与版本元数据。 */
    public record ProvenanceResponse(String source, String commit, String filePath, Integer lineNumber,
                                     String ruleVersion, String extractedAt) { }
}
