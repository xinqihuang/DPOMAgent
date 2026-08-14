package com.dpom.agent.core.stacktrace;

import com.dpom.agent.common.codegraph.Symbol;

import java.util.List;

/**
 * 代码图证据：来自 CodeGraphContext 的导航结果。
 *
 * @param queryType 查询类型（如 findCallers）
 * @param symbol    查询的符号
 * @param result    查询结果
 */
public record GraphEvidence(String queryType, String symbol, List<Symbol> result) {
}
