package com.dpom.agent.web.dto;

import java.util.List;

/**
 * 证据束审计视图 DTO。available=false 表示证据尚未生成（区别于 404 不存在）。
 */
public record EvidenceResponse(boolean available, String service, String environment, String release, String commit,
        String timeRange, List<LogEvidenceResponse> logEvidences, List<CodeAnchorResponse> anchors,
        List<CodeEvidenceResponse> codeEvidences, List<String> degradations, List<String> contradictions,
        boolean truncated) {

    /** 证据尚未生成的稳定响应。 */
    public static EvidenceResponse notReady() {
        return new EvidenceResponse(false, null, null, null, null, null, null, null, null, null, null, false);
    }
}
