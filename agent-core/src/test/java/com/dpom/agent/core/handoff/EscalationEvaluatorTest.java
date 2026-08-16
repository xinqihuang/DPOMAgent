package com.dpom.agent.core.handoff;

import com.dpom.agent.core.hypothesis.HypothesisStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 升级判定器单元测试：置信度阈值、矛盾、缺失证据、代码级证明与确定性。
 */
class EscalationEvaluatorTest {

    private final EscalationEvaluator evaluator = new EscalationEvaluator();
    private final HandoffConfig config = HandoffConfig.defaults();

    @Test
    void rootCauseWithVerifiedSourceIsNotEligible() {
        EscalationDecision d = evaluator.evaluate(
                new EscalationContext("ROOT_CAUSE_FOUND", HypothesisStatus.VALIDATED, true, List.of(), List.of(), false),
                config);
        assertThat(d.eligible()).isFalse();
        assertThat(d.confidence()).isEqualTo(95);
    }

    @Test
    void lowConfidenceIsEligible() {
        EscalationDecision d = evaluator.evaluate(
                new EscalationContext(null, null, false, List.of(), List.of(), false), config);
        assertThat(d.eligible()).isTrue();
        assertThat(d.reasons()).contains(EscalationReason.LOW_CONFIDENCE);
        assertThat(d.confidence()).isLessThan(config.confidenceThreshold());
    }

    @Test
    void unresolvedContradictionIsEligible() {
        EscalationDecision d = evaluator.evaluate(
                new EscalationContext("ROOT_CAUSE_FOUND", null, true, List.of("a vs b"), List.of(), false), config);
        assertThat(d.eligible()).isTrue();
        assertThat(d.reasons()).contains(EscalationReason.UNRESOLVED_CONTRADICTION);
    }

    @Test
    void missingEvidenceIsEligible() {
        EscalationDecision d = evaluator.evaluate(
                new EscalationContext("ROOT_CAUSE_FOUND", null, true, List.of(), List.of("source unavailable"), false),
                config);
        assertThat(d.eligible()).isTrue();
        assertThat(d.reasons()).contains(EscalationReason.MISSING_EVIDENCE);
        assertThat(d.missingEvidence()).containsExactly("source unavailable");
    }

    @Test
    void requiresCodeProofIsEligible() {
        EscalationDecision d = evaluator.evaluate(
                new EscalationContext("ROOT_CAUSE_FOUND", null, true, List.of(), List.of(), true), config);
        assertThat(d.eligible()).isTrue();
        assertThat(d.reasons()).contains(EscalationReason.CODE_PROOF_REQUIRED);
    }

    @Test
    void deterministicForSameInputs() {
        EscalationContext ctx = new EscalationContext("INCONCLUSIVE", HypothesisStatus.INCONCLUSIVE, false,
                List.of("c"), List.of("m"), true);
        EscalationDecision first = evaluator.evaluate(ctx, config);
        EscalationDecision second = evaluator.evaluate(ctx, config);
        assertThat(second).isEqualTo(first);
        assertThat(second.confidence()).isEqualTo(first.confidence());
    }
}
