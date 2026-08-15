package com.dpom.agent.core.eval;

import java.util.List;

/**
 * 单个回归案例结果。
 *
 * @param caseId                 案例 id
 * @param executed               是否真实执行
 * @param passed                 断言是否全部通过
 * @param status                 PASSED / FAILED / NOT_EXECUTED
 * @param resultType             结论类型
 * @param actualRootCauseId      实际 rootCauseId
 * @param expectedRootCauseId    期望 rootCauseId
 * @param logEvidenceIds         日志证据引用
 * @param sourceEvidenceIds      源码证据引用
 * @param expectedSymbolsMatched 命中的 expectedSymbols
 * @param toolCalls              工具调用次数
 * @param latencyMs              耗时毫秒
 * @param failures               失败原因
 */
public record BenchmarkCaseResult(String caseId, boolean executed, boolean passed, String status,
                                  String resultType, String actualRootCauseId, String expectedRootCauseId,
                                  List<String> logEvidenceIds, List<String> sourceEvidenceIds,
                                  List<String> expectedSymbolsMatched, long toolCalls, long latencyMs,
                                  List<String> failures) {
}
