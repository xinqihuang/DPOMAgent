package com.dpom.agent.core.handoff;

import com.dpom.agent.core.hypothesis.HypothesisStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 升级判定器：把调查状态投影为确定性 EscalationDecision。
 */
public class EscalationEvaluator {

    /**
     * 计算升级判定。
     *
     * @param ctx    升级输入
     * @param config 交接配置
     * @return 确定性升级判定
     */
    public EscalationDecision evaluate(EscalationContext ctx, HandoffConfig config) {
        int confidence = confidence(ctx);
        List<EscalationReason> reasons = new ArrayList<>();
        if (confidence < config.confidenceThreshold()) {
            reasons.add(EscalationReason.LOW_CONFIDENCE);
        }
        if (ctx.contradictions() != null && !ctx.contradictions().isEmpty()) {
            reasons.add(EscalationReason.UNRESOLVED_CONTRADICTION);
        }
        if (ctx.missingEvidence() != null && !ctx.missingEvidence().isEmpty()) {
            reasons.add(EscalationReason.MISSING_EVIDENCE);
        }
        if (ctx.requiresCodeProof()) {
            reasons.add(EscalationReason.CODE_PROOF_REQUIRED);
        }
        List<String> missing = ctx.missingEvidence() == null ? List.of() : List.copyOf(ctx.missingEvidence());
        return new EscalationDecision(!reasons.isEmpty(), List.copyOf(reasons), missing, confidence);
    }

    /**
     * 确定性置信度投影：ROOT_CAUSE_FOUND(有源码 95/无源码 70) → VALIDATED 60 → INCONCLUSIVE 30/20 → 其余 10。
     */
    private int confidence(EscalationContext ctx) {
        if ("ROOT_CAUSE_FOUND".equals(ctx.resultType())) {
            return ctx.hasVerifiedSource() ? 95 : 70;
        }
        if (ctx.highestHypothesisStatus() == HypothesisStatus.VALIDATED) {
            return 60;
        }
        if (ctx.highestHypothesisStatus() == HypothesisStatus.INCONCLUSIVE) {
            return 30;
        }
        if ("INCONCLUSIVE".equals(ctx.resultType())) {
            return 20;
        }
        return 10;
    }
}
