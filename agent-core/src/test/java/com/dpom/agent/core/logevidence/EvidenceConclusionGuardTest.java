package com.dpom.agent.core.logevidence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T106 结论护栏单元验收。
 */
class EvidenceConclusionGuardTest {

    private static LogEvidence log(String id) {
        LogTemplateSummary s = new LogTemplateSummary(1, "t", 1, null, null, Map.of("ERROR", 1),
                List.of("s"), new ParameterDistribution(Map.of()), false);
        return new LogEvidence(id, s, "svc", "prod", "1.0.0", "c", "1h", null, "drain3-0.9",
                new EvidenceProvenance("drain3", "c", null, null, "v1", null));
    }

    private static CodeEvidence source(String id) {
        return new CodeEvidence(id, "a", "sym", "F.java", 1, "c", "ex", "VERIFIED");
    }

    private static EvidenceBundle bundle(List<LogEvidence> logs, List<CodeEvidence> codes) {
        return new EvidenceBundle("svc", "prod", "1.0.0", "c", "1h", logs, List.of(), codes, List.of(), List.of(), false);
    }

    @Test
    void withLogAndSourceAllowsRootCause() {
        EvidenceBundle b = bundle(List.of(log("ev-1")), List.of(source("code-1")));
        assertThat(EvidenceConclusionGuard.validate(b, "ROOT_CAUSE_FOUND", "ev-1,code-1")).isEqualTo("ROOT_CAUSE_FOUND");
    }

    @Test
    void onlyLogRejectsRootCause() {
        EvidenceBundle b = bundle(List.of(log("ev-1")), List.of());
        assertThat(EvidenceConclusionGuard.validate(b, "ROOT_CAUSE_FOUND", "ev-1")).isEqualTo("INCONCLUSIVE");
    }

    @Test
    void danglingEvidenceIdRejectsRootCause() {
        EvidenceBundle b = bundle(List.of(log("ev-1")), List.of(source("code-1")));
        assertThat(EvidenceConclusionGuard.validate(b, "ROOT_CAUSE_FOUND", "ev-1,code-999")).isEqualTo("INCONCLUSIVE");
    }

    @Test
    void nullBundlePassesThrough() {
        assertThat(EvidenceConclusionGuard.validate(null, "ROOT_CAUSE_FOUND", "x")).isEqualTo("ROOT_CAUSE_FOUND");
    }

    @Test
    void nonRootCausePassesThrough() {
        EvidenceBundle b = bundle(List.of(log("ev-1")), List.of());
        assertThat(EvidenceConclusionGuard.validate(b, "WAITING_FOR_HUMAN", "")).isEqualTo("WAITING_FOR_HUMAN");
    }
}
