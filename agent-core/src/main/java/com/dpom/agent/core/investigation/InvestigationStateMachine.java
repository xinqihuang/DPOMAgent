package com.dpom.agent.core.investigation;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 调查状态机：定义合法状态迁移，非法迁移抛出 {@link IllegalStateTransitionException}。
 */
@Component
public final class InvestigationStateMachine {

    private final Map<InvestigationStatus, Set<InvestigationStatus>> transitions =
            new EnumMap<>(InvestigationStatus.class);

    /**
     * 构造状态机并初始化合法迁移。
     */
    public InvestigationStateMachine() {
        init();
    }

    private void init() {
        transitions.put(InvestigationStatus.CREATED,
                EnumSet.of(InvestigationStatus.SCOPING));
        transitions.put(InvestigationStatus.SCOPING,
                EnumSet.of(InvestigationStatus.RESEARCHING, InvestigationStatus.WAITING_FOR_HUMAN,
                        InvestigationStatus.SYNTHESIZING, InvestigationStatus.INCONCLUSIVE,
                        InvestigationStatus.CANCELLED, InvestigationStatus.FAILED));
        transitions.put(InvestigationStatus.RESEARCHING,
                EnumSet.of(InvestigationStatus.FORMING_HYPOTHESES, InvestigationStatus.WAITING_FOR_HUMAN,
                        InvestigationStatus.SYNTHESIZING, InvestigationStatus.INCONCLUSIVE,
                        InvestigationStatus.CANCELLED, InvestigationStatus.FAILED));
        transitions.put(InvestigationStatus.FORMING_HYPOTHESES,
                EnumSet.of(InvestigationStatus.VALIDATING, InvestigationStatus.WAITING_FOR_HUMAN,
                        InvestigationStatus.SYNTHESIZING, InvestigationStatus.INCONCLUSIVE,
                        InvestigationStatus.CANCELLED));
        transitions.put(InvestigationStatus.VALIDATING,
                EnumSet.of(InvestigationStatus.SYNTHESIZING, InvestigationStatus.WAITING_FOR_HUMAN,
                        InvestigationStatus.INCONCLUSIVE, InvestigationStatus.CANCELLED));
        transitions.put(InvestigationStatus.WAITING_FOR_HUMAN,
                EnumSet.of(InvestigationStatus.RESEARCHING, InvestigationStatus.VALIDATING,
                        InvestigationStatus.SYNTHESIZING, InvestigationStatus.CANCELLED));
        transitions.put(InvestigationStatus.SYNTHESIZING,
                EnumSet.of(InvestigationStatus.COMPLETED, InvestigationStatus.INCONCLUSIVE,
                        InvestigationStatus.FAILED));
    }

    /**
     * 判断迁移是否合法。
     *
     * @param from 源状态
     * @param to   目标状态
     * @return 是否合法
     */
    public boolean canTransition(InvestigationStatus from, InvestigationStatus to) {
        Set<InvestigationStatus> allowed = transitions.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * 校验迁移合法性，非法则抛出异常。
     *
     * @param from 源状态
     * @param to   目标状态
     */
    public void assertTransition(InvestigationStatus from, InvestigationStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateTransitionException(from, to);
        }
    }
}
