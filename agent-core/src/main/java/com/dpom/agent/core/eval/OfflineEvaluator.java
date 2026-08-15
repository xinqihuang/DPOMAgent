package com.dpom.agent.core.eval;

import com.dpom.agent.core.logevidence.CodeEvidence;
import com.dpom.agent.core.logevidence.EvidenceBundle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 离线评测器：对证据束执行机器可读断言（rootCauseId/expectedSymbols/requiredEvidenceTypes/forbiddenConclusions）。
 */
public class OfflineEvaluator {

    /**
     * 评测证据束。
     *
     * @param c      评测案例
     * @param bundle 证据束
     * @return 失败原因列表（空表示通过）
     */
    public List<String> evaluate(EvalCase c, EvidenceBundle bundle) {
        List<String> failures = new ArrayList<>();
        Set<String> present = new HashSet<>();
        if (!bundle.logEvidences().isEmpty()) {
            present.add("LOG");
        }
        if (bundle.hasVerifiedSource()) {
            present.add("SOURCE");
        }
        if (!bundle.anchors().isEmpty()) {
            present.add("GRAPH");
        }
        for (String type : c.expected().requiredEvidenceTypes()) {
            if (!present.contains(type)) {
                failures.add("MISSING_EVIDENCE_TYPE:" + type);
            }
        }
        Set<String> symbols = bundle.codeEvidences().stream().map(CodeEvidence::symbol).collect(Collectors.toSet());
        for (String symbol : c.expected().expectedSymbols()) {
            if (!symbols.contains(symbol)) {
                failures.add("MISSING_SYMBOL:" + symbol);
            }
        }
        if (c.expected().rootCauseId() != null && !symbols.contains(c.expected().rootCauseId())) {
            failures.add("MISSING_ROOT_CAUSE:" + c.expected().rootCauseId());
        }
        for (String forbidden : c.expected().forbiddenConclusions()) {
            if ("ROOT_CAUSE_FOUND_WITHOUT_SOURCE".equals(forbidden) && !bundle.hasVerifiedSource()) {
                failures.add("FORBIDDEN:" + forbidden);
            }
        }
        return failures;
    }
}
