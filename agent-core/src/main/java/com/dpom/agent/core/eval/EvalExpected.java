package com.dpom.agent.core.eval;

import java.util.List;

/**
 * 评测期望：机器可读断言目标。
 *
 * @param rootCauseId             预期根因标识（代码符号）
 * @param expectedSymbols         预期解析到的符号列表
 * @param requiredEvidenceTypes   必须存在的证据类型（LOG/SOURCE/GRAPH）
 * @param forbiddenConclusions    禁止出现的结果（如 ROOT_CAUSE_FOUND_WITHOUT_SOURCE）
 */
public record EvalExpected(String rootCauseId, List<String> expectedSymbols,
                           List<String> requiredEvidenceTypes, List<String> forbiddenConclusions) {
}
