package com.dpom.agent.core.handoff;

import com.dpom.agent.core.logevidence.EvidenceBundle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 证据包解析器单元测试：恢复为现有 EvidenceBundle。
 */
class DiagnosticEvidencePackageParserTest {

    private final DiagnosticEvidencePackageParser parser = new DiagnosticEvidencePackageParser();

    @Test
    void recoversEvidenceBundleWithoutSource() {
        RecoveredEvidencePackage pkg = new RecoveredEvidencePackage(1, "p1", "svc", "env", "rel", "commit", "1h",
                Map.of("logs", List.of("template A count=1", "template B count=2"),
                        "degradations", List.of("LOG_MINER_UNAVAILABLE"),
                        "contradictions", List.of("a vs b")));
        EvidenceBundle bundle = parser.recover(pkg);
        assertThat(bundle.service()).isEqualTo("svc");
        assertThat(bundle.release()).isEqualTo("rel");
        assertThat(bundle.commit()).isEqualTo("commit");
        assertThat(bundle.logEvidences()).hasSize(2);
        assertThat(bundle.degradations()).containsExactly("LOG_MINER_UNAVAILABLE");
        assertThat(bundle.contradictions()).containsExactly("a vs b");
        assertThat(bundle.hasVerifiedSource()).isFalse();
    }
}
