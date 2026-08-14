package com.dpom.agent.core.investigation;

import java.util.List;

/**
 * 工具执行结果：一条观察（证据）加上由此带来的假设新建/更新。
 *
 * @param source                证据来源
 * @param location              证据位置（可为空）
 * @param summary               证据摘要
 * @param supportsHypothesisIds 支持的假设 id（逗号分隔，可为空）
 * @param contradictsHypothesisIds 反驳的假设 id（逗号分隔，可为空）
 * @param newHypotheses         新建假设描述列表（可为空）
 * @param hypothesisUpdates     已有假设状态更新列表（可为空）
 */
public record ToolExecutionResult(String source, String location, String summary,
                                  String supportsHypothesisIds, String contradictsHypothesisIds,
                                  List<String> newHypotheses, List<HypothesisUpdate> hypothesisUpdates) {

    /**
     * 便捷构造：无新建假设、无状态更新。
     *
     * @param source  来源
     * @param summary 摘要
     */
    public ToolExecutionResult(String source, String summary) {
        this(source, null, summary, null, null, List.of(), List.of());
    }
}
