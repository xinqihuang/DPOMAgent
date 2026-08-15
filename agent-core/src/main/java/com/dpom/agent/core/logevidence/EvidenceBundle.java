package com.dpom.agent.core.logevidence;

import java.util.List;

/**
 * 证据束：本轮 LLM 输入的日志到代码证据载体，带来源、降级与矛盾信息。
 *
 * @param service       服务编码
 * @param environment   环境
 * @param release       发布版本
 * @param commit        提交 SHA
 * @param timeRange     时间范围
 * @param logEvidences  日志证据（已聚合、脱敏）
 * @param anchors       代码锚点
 * @param codeEvidences 版本绑定的代码证据
 * @param degradations  降级标记（如 LOG_MINER_UNAVAILABLE）
 * @param contradictions 矛盾/缺失证据标记
 * @param truncated     是否因预算被截断
 */
public record EvidenceBundle(String service, String environment, String release, String commit, String timeRange,
                             List<LogEvidence> logEvidences, List<CodeAnchor> anchors, List<CodeEvidence> codeEvidences,
                             List<String> degradations, List<String> contradictions, boolean truncated) {

    /**
     * 是否存在已验证源码证据（用于结论护栏）。
     *
     * @return true 当且仅当存在 VERIFIED 状态的代码证据
     */
    public boolean hasVerifiedSource() {
        return codeEvidences != null && codeEvidences.stream().anyMatch(e -> "VERIFIED".equals(e.status()));
    }
}
