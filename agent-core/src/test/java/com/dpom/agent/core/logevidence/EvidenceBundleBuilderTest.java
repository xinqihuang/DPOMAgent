package com.dpom.agent.core.logevidence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T106 证据束构建与结论护栏验收。
 */
class EvidenceBundleBuilderTest {

    private static LogEvidence log(String id, String level, int count) {
        LogTemplateSummary s = new LogTemplateSummary(1, "device <*> insert failed", count, null, null,
                Map.of(level, count), List.of("sample"), new ParameterDistribution(Map.of()), false);
        return new LogEvidence(id, s, "s", "prod", "1.0.0", "c", "1h", null, "drain3-0.9",
                new EvidenceProvenance("drain3", "c", null, null, "v1", null));
    }

    /**
     * 已验证源码优先，ERROR 日志按频次降序。
     */
    @Test
    void ordersVerifiedSourceAndErrorFirst() {
        EvidenceBundleBuilder builder = new EvidenceBundleBuilder(100_000);
        CodeEvidence verified = new CodeEvidence("code-1", "a", "Svc.insert", "Svc.java", 42, "c", "x", "VERIFIED");
        CodeEvidence fallback = new CodeEvidence("code-2", "a", "a", "Svc.java", 1, "c", "x", "WORKSPACE_FALLBACK");
        LogEvidence err = log("e1", "ERROR", 20);
        LogEvidence info = log("e2", "INFO", 50);

        EvidenceBundle b = builder.build("s", "prod", "1.0.0", "c", "1h",
                List.of(info, err), List.of(), List.of(fallback, verified), List.of("DEGRADED"), List.of("CONTRADICTION"));

        assertThat(b.codeEvidences().get(0).status()).isEqualTo("VERIFIED");
        assertThat(b.logEvidences().get(0).summary().severityDistribution()).containsKey("ERROR");
        assertThat(b.degradations()).contains("DEGRADED");
        assertThat(b.contradictions()).contains("CONTRADICTION");
    }

    /**
     * 预算不足时截断并标记。
     */
    @Test
    void truncatesWhenOverBudget() {
        EvidenceBundleBuilder builder = new EvidenceBundleBuilder(50);
        EvidenceBundle b = builder.build("s", "prod", "1.0.0", "c", "1h",
                List.of(log("e1", "ERROR", 10), log("e2", "WARN", 5)), List.of(), List.of(), List.of(), List.of());
        assertThat(b.truncated()).isTrue();
    }

    /**
     * 无已验证源码时不得允许 ROOT_CAUSE_FOUND。
     */
    @Test
    void noVerifiedSourceForbidsRootCause() {
        EvidenceBundle noSource = new EvidenceBundle("s", "prod", "1.0.0", "c", "1h",
                List.of(log("e1", "ERROR", 1)), List.of(), List.of(), List.of(), List.of(), false);
        assertThat(noSource.hasVerifiedSource()).isFalse();

        EvidenceBundle withSource = new EvidenceBundle("s", "prod", "1.0.0", "c", "1h",
                List.of(), List.of(),
                List.of(new CodeEvidence("code-1", "a", "Svc.insert", "Svc.java", 1, "c", "x", "VERIFIED")),
                List.of(), List.of(), false);
        assertThat(withSource.hasVerifiedSource()).isTrue();
    }
}
