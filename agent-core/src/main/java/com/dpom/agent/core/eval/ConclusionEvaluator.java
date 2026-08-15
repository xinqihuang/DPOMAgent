package com.dpom.agent.core.eval;

import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.logevidence.CodeEvidence;
import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.dpom.agent.core.logevidence.LogEvidence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 结论评测器：对真实 Conclusion + EvidenceBundle 执行 fixture 断言，rootCauseId 只来自实际 Conclusion。
 */
public class ConclusionEvaluator {

    /**
     * 评测结论。
     *
     * @param conclusion 最终结论
     * @param bundle     证据束
     * @param expected   期望
     * @return 评测结果
     */
    public ConclusionEvaluation evaluate(Conclusion conclusion, EvidenceBundle bundle, EvalExpected expected) {
        List<String> failures = new ArrayList<>();
        Set<String> refs = split(conclusion.evidenceIds());
        Set<String> logIds = bundle.logEvidences().stream().map(LogEvidence::evidenceId).collect(Collectors.toSet());
        Set<String> verifiedIds = bundle.codeEvidences().stream()
                .filter(e -> "VERIFIED".equals(e.status())).map(CodeEvidence::evidenceId).collect(Collectors.toSet());
        Set<String> allCodeIds = bundle.codeEvidences().stream().map(CodeEvidence::evidenceId).collect(Collectors.toSet());

        boolean allExist = refs.stream().allMatch(id -> logIds.contains(id) || allCodeIds.contains(id));
        if (!allExist) {
            failures.add("DANGLING_REFERENCE");
        }
        if (refs.stream().noneMatch(logIds::contains)) {
            failures.add("MISSING_LOG_REFERENCE");
        }
        if (refs.stream().noneMatch(verifiedIds::contains)) {
            failures.add("MISSING_VERIFIED_SOURCE_REFERENCE");
        }
        Set<String> verifiedSymbols = bundle.codeEvidences().stream()
                .filter(e -> "VERIFIED".equals(e.status())).map(CodeEvidence::symbol).collect(Collectors.toSet());
        for (String symbol : expected.expectedSymbols()) {
            if (!verifiedSymbols.contains(symbol)) {
                failures.add("MISSING_SYMBOL:" + symbol);
            }
        }
        if ("ROOT_CAUSE_FOUND".equals(conclusion.resultType()) && !bundle.hasVerifiedSource()) {
            failures.add("FORBIDDEN:ROOT_CAUSE_FOUND_WITHOUT_SOURCE");
        }
        // rootCauseId 只来自实际 Conclusion，与 expected 精确比较。
        String actualRootCauseId = conclusion.rootCauseId();
        if (expected.rootCauseId() != null) {
            if (actualRootCauseId == null || actualRootCauseId.isBlank()) {
                failures.add("ROOT_CAUSE_MISMATCH:empty");
            } else if (!actualRootCauseId.equals(expected.rootCauseId())) {
                failures.add("ROOT_CAUSE_MISMATCH:" + actualRootCauseId);
            }
        }
        if (actualRootCauseId != null && !actualRootCauseId.isBlank() && !verifiedSymbols.contains(actualRootCauseId)) {
            failures.add("ROOT_CAUSE_ID_NOT_VERIFIED:" + actualRootCauseId);
        }

        List<String> logEvidenceIds = refs.stream().filter(logIds::contains).sorted().toList();
        List<String> sourceEvidenceIds = refs.stream().filter(verifiedIds::contains).sorted().toList();
        List<String> symbolsMatched = expected.expectedSymbols().stream().filter(verifiedSymbols::contains).toList();
        return new ConclusionEvaluation(failures, actualRootCauseId, logEvidenceIds, sourceEvidenceIds, symbolsMatched);
    }

    /**
     * 拆分逗号分隔 id。
     */
    private Set<String> split(String ids) {
        if (ids == null || ids.isBlank()) {
            return new HashSet<>();
        }
        return Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }
}
