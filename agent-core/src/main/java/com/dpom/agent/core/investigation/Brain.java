package com.dpom.agent.core.investigation;

/**
 * 调查大脑：根据当前上下文决策下一步动作、等待人工或结论。
 */
@FunctionalInterface
public interface Brain {

    /**
     * 决策。
     *
     * @param context 当前上下文
     * @return 决策
     */
    InvestigationDecision decide(InvestigationContext context);
}
