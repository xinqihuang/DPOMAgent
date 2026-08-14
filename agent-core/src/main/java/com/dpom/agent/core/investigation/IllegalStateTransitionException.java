package com.dpom.agent.core.investigation;

/**
 * 非法状态迁移异常。
 */
public class IllegalStateTransitionException extends RuntimeException {

    /**
     * 构造异常。
     *
     * @param from 源状态
     * @param to   目标状态
     */
    public IllegalStateTransitionException(InvestigationStatus from, InvestigationStatus to) {
        super("非法状态迁移：" + from + " -> " + to);
    }
}
