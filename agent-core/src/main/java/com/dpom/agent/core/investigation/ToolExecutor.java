package com.dpom.agent.core.investigation;

/**
 * 受限工具执行器：执行一个动作并返回观察与假设更新。
 */
@FunctionalInterface
public interface ToolExecutor {

    /**
     * 执行动作。
     *
     * @param action 动作
     * @return 执行结果
     */
    ToolExecutionResult execute(ToolAction action);
}
