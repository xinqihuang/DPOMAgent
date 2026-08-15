package com.dpom.agent.core.investigation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 状态机验收：LLM 在早期轮次（SCOPING）直接 conclude 是合法收口路径。
 */
class InvestigationStateMachineTest {

    @Test
    void scopingCanConcludeDirectly() {
        InvestigationStateMachine sm = new InvestigationStateMachine();
        assertThat(sm.canTransition(InvestigationStatus.SCOPING, InvestigationStatus.SYNTHESIZING)).isTrue();
        assertThat(sm.canTransition(InvestigationStatus.SCOPING, InvestigationStatus.INCONCLUSIVE)).isTrue();
        assertThat(sm.canTransition(InvestigationStatus.SYNTHESIZING, InvestigationStatus.COMPLETED)).isTrue();
        assertThat(sm.canTransition(InvestigationStatus.SYNTHESIZING, InvestigationStatus.INCONCLUSIVE)).isTrue();
    }

    @Test
    void normalFlowStillValid() {
        InvestigationStateMachine sm = new InvestigationStateMachine();
        assertThat(sm.canTransition(InvestigationStatus.CREATED, InvestigationStatus.SCOPING)).isTrue();
        assertThat(sm.canTransition(InvestigationStatus.SCOPING, InvestigationStatus.RESEARCHING)).isTrue();
        assertThat(sm.canTransition(InvestigationStatus.RESEARCHING, InvestigationStatus.FORMING_HYPOTHESES)).isTrue();
        assertThat(sm.canTransition(InvestigationStatus.FORMING_HYPOTHESES, InvestigationStatus.VALIDATING)).isTrue();
        assertThat(sm.canTransition(InvestigationStatus.VALIDATING, InvestigationStatus.SYNTHESIZING)).isTrue();
    }
}
