package com.dpom.agent.core.handoff;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * 证据包构建器单元测试：allow-list、脱敏、禁止字段、大小/条数上限。
 */
class DiagnosticEvidencePackageBuilderTest {

    private final DiagnosticEvidencePackageBuilder builder =
            new DiagnosticEvidencePackageBuilder(HandoffConfig.defaults());

    @Test
    void buildsRedactedSections() {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        sections.put("logs", List.of("token=abc123", "level=INFO request ok"));
        DiagnosticEvidencePackage pkg = builder.build("p1", "svc", "env", "rel", "commit", "1h", sections);
        assertThat(pkg.packageId()).isEqualTo("p1");
        assertThat(pkg.sections().get("logs").get(0)).startsWith("token=h:").doesNotContain("abc123");
    }

    @Test
    void rejectsUnknownSection() {
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> builder.build("p1", "svc", "env", "rel", "commit", "1h",
                        Map.of("secret-dump", List.of("x"))))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.PACKAGE_INVALID);
    }

    @Test
    void rejectsSourceMarker() {
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> builder.build("p1", "svc", "env", "rel", "commit", "1h",
                        Map.of("logs", List.of("package com.foo;"))))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.FORBIDDEN_CONTENT);
    }

    @Test
    void rejectsAkSkAssignment() {
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> builder.build("p1", "svc", "env", "rel", "commit", "1h",
                        Map.of("logs", List.of("AK=ABCDEFGHIJKLMNOP"))))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.FORBIDDEN_CONTENT);
    }

    @Test
    void rejectsUnredactedCredentialKey() {
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> builder.build("p1", "svc", "env", "rel", "commit", "1h",
                        Map.of("logs", List.of("cookie=sessionid123"))))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.FORBIDDEN_CONTENT);
    }

    @Test
    void rejectsTooManyEntries() {
        HandoffConfig small = new HandoffConfig(HandoffProfile.DEVELOPMENT, 60, 1_000_000, 2, 1, false, "", "", 3600);
        DiagnosticEvidencePackageBuilder smallBuilder = new DiagnosticEvidencePackageBuilder(small);
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> smallBuilder.build("p1", "svc", "env", "rel", "commit", "1h",
                        Map.of("logs", List.of("a", "b", "c"))))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.ENTRIES_EXCEEDED);
    }

    @Test
    void rejectsTooManyBytes() {
        HandoffConfig small = new HandoffConfig(HandoffProfile.DEVELOPMENT, 60, 8, 100, 1, false, "", "", 3600);
        DiagnosticEvidencePackageBuilder smallBuilder = new DiagnosticEvidencePackageBuilder(small);
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> smallBuilder.build("p1", "svc", "env", "rel", "commit", "1h",
                        Map.of("logs", List.of("0123456789"))))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.SIZE_EXCEEDED);
    }
}
