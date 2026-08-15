package com.dpom.agent.core.eval;

import java.util.List;

/**
 * 结论评测结果：失败原因 + 派生的机器可读字段。
 *
 * @param failures              失败原因（空表示通过）
 * @param rootCauseId           派生根因标识（如 AssetRepository.insert）
 * @param logEvidenceIds        结论引用且存在的日志证据 id
 * @param sourceEvidenceIds     结论引用且 VERIFIED 的源码证据 id
 * @param expectedSymbolsMatched 命中的 expectedSymbols
 */
public record ConclusionEvaluation(List<String> failures, String rootCauseId, List<String> logEvidenceIds,
                                   List<String> sourceEvidenceIds, List<String> expectedSymbolsMatched) {

    /**
     * 是否通过。
     *
     * @return true 当无失败原因
     */
    public boolean passed() {
        return failures.isEmpty();
    }
}
