package com.dpom.agent.core.investigation;

import java.util.List;

/**
 * 调查决策：执行动作、等待人工或给出结论（三者互斥）。
 */
public sealed interface InvestigationDecision {

    /**
     * 执行一个工具动作。
     *
     * @param action 动作
     */
    record Act(ToolAction action) implements InvestigationDecision {
    }

    /**
     * 等待人工反馈。
     *
     * @param reason 原因
     */
    record WaitForHuman(String reason) implements InvestigationDecision {
    }

    /**
     * 给出结论。
     *
     * @param resultType 结论类型
     * @param rootCauseId 稳定根因标识（类.方法，可为空）
     * @param rootCause  根因自然语言描述
     * @param summary    摘要
     * @param evidenceIds 证据 id（逗号分隔，可为空）
     */
    record Conclude(String resultType, String rootCauseId, String rootCause, String summary, String evidenceIds)
            implements InvestigationDecision {
    }

    /**
     * 解释证据并更新假设（不调用工具）。
     *
     * @param newHypotheses      新建假设描述列表
     * @param hypothesisUpdates  已有假设状态更新列表
     */
    record UpdateHypotheses(List<String> newHypotheses, List<HypothesisUpdate> hypothesisUpdates)
            implements InvestigationDecision {
    }
}