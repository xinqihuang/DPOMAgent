package com.dpom.agent.core.eval;

import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.logevidence.CodeEvidence;
import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.dpom.agent.core.logevidence.EvidenceProvenance;
import com.dpom.agent.core.logevidence.LogEvidence;
import com.dpom.agent.core.logevidence.LogTemplateSummary;
import com.dpom.agent.core.logevidence.ParameterDistribution;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConclusionEvaluator 负向验收：rootCauseId 必须来自实际 Conclusion，不得由 expected 自证。
 */
class ConclusionEvaluatorTest {

    private static LogEvidence log(String id) {
        LogTemplateSummary s = new LogTemplateSummary(1, "t", 1, null, null, Map.of("ERROR", 1),
                List.of("s"), new ParameterDistribution(Map.of()), false);
        return new LogEvidence(id, s, "svc", "prod", "1.0.0", "c", "1h", null, "drain3-0.9",
                new EvidenceProvenance("drain3", "c", null, null, "v1", null));
    }

    private static CodeEvidence source(String id, String symbol) {
        return new CodeEvidence(id, "a", symbol, "F.java", 1, "c", "ex", "VERIFIED");
    }

    private static EvidenceBundle bundle(List<LogEvidence> logs, List<CodeEvidence> codes) {
        return new EvidenceBundle("svc", "prod", "1.0.0", "c", "1h", logs, List.of(), codes, List.of(), List.of(), false);
    }

    @Test
    void wrongRootCauseIdFails() {
        EvidenceBundle b = bundle(List.of(log("ev-1")),
                List.of(source("code-1", "AssetRepository.insert"), source("code-2", "AssetService.create")));
        EvalExpected expected = new EvalExpected("AssetRepository.insert",
                List.of("AssetRepository.insert", "AssetService.create"), List.of(), List.of());
        Conclusion conclusion = new Conclusion(null, 1L, "ROOT_CAUSE_FOUND", "AssetService.create", "wrong",
                "ev-1,code-1,code-2", null, "s", null);

        ConclusionEvaluation eval = new ConclusionEvaluator().evaluate(conclusion, b, expected);

        assertThat(eval.failures()).contains("ROOT_CAUSE_MISMATCH:AssetService.create");
        assertThat(eval.passed()).isFalse();
    }

    @Test
    void correctRootCauseIdPasses() {
        EvidenceBundle b = bundle(List.of(log("ev-1")),
                List.of(source("code-1", "AssetRepository.insert"), source("code-2", "AssetService.create")));
        EvalExpected expected = new EvalExpected("AssetRepository.insert",
                List.of("AssetRepository.insert", "AssetService.create"), List.of(), List.of());
        Conclusion conclusion = new Conclusion(null, 1L, "ROOT_CAUSE_FOUND", "AssetRepository.insert", "correct",
                "ev-1,code-1,code-2", null, "s", null);

        ConclusionEvaluation eval = new ConclusionEvaluator().evaluate(conclusion, b, expected);

        assertThat(eval.failures()).isEmpty();
        assertThat(eval.rootCauseId()).isEqualTo("AssetRepository.insert");
    }

    /**
     * 缺少 LOG 或 VERIFIED SOURCE 引用必须失败。
     */
    @Test
    void missingReferencesFail() {
        EvidenceBundle b = bundle(List.of(log("ev-1")),
                List.of(source("code-1", "AssetRepository.insert")));
        EvalExpected expected = new EvalExpected("AssetRepository.insert",
                List.of("AssetRepository.insert"), List.of(), List.of());
        Conclusion noLog = new Conclusion(null, 1L, "ROOT_CAUSE_FOUND", "AssetRepository.insert", "r",
                "code-1", null, "s", null);
        assertThat(new ConclusionEvaluator().evaluate(noLog, b, expected).failures()).contains("MISSING_LOG_REFERENCE");

        Conclusion noSource = new Conclusion(null, 1L, "ROOT_CAUSE_FOUND", "AssetRepository.insert", "r",
                "ev-1", null, "s", null);
        assertThat(new ConclusionEvaluator().evaluate(noSource, b, expected).failures())
                .contains("MISSING_VERIFIED_SOURCE_REFERENCE");
    }
}
