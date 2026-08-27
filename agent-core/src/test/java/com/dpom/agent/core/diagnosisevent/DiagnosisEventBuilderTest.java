package com.dpom.agent.core.diagnosisevent;

import com.dpom.agent.common.diagnosisevent.DiagnosisEventProvenance;
import com.dpom.agent.common.diagnosisevent.ProvenanceSource;
import com.dpom.agent.common.diagnosisevent.ProvenanceVersion;
import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationRun;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Diagnosis Event 构造与边界测试。
 */
class DiagnosisEventBuilderTest {

    private DiagnosisEventBuilder builder;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        builder = new DiagnosisEventBuilder(mapper, new Rfc8785CanonicalJsonWriter(mapper));
    }

    @Test
    void buildsCanonicalEventFromPersistedFacts() {
        BuiltDiagnosisEvent built = builder.build(incident(), investigation(), conclusion("summary"), run(),
                metadata(), provenance());

        assertThat(built.event().incidentId()).isEqualTo("10");
        assertThat(built.event().investigationId()).isEqualTo("20");
        assertThat(built.event().runId()).isEqualTo("30");
        assertThat(built.event().inlinePayload()).isNotNull();
        assertThat(built.event().artifactRef()).isNull();
        assertThat(built.canonicalBytes()).hasSizeLessThanOrEqualTo(DiagnosisEventBuilder.MAX_EVENT_BYTES);
        assertThat(built.canonicalSha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void rejectsOversizedInlinePayload() {
        assertThatThrownBy(() -> builder.build(incident(), investigation(), conclusion("x".repeat(17_000)), run(),
                metadata(), provenance()))
                .isInstanceOf(DiagnosisEventValidationException.class)
                .hasMessage("PAYLOAD_TOO_LARGE");
    }

    @Test
    void rejectsInventedUnavailableProvenance() {
        DiagnosisEventProvenance invalid = new DiagnosisEventProvenance(
                ProvenanceVersion.available("DPOMAgent", "1.0", null),
                ProvenanceVersion.unavailable(""), ProvenanceVersion.unavailable("NOT_RECORDED"),
                List.of(ProvenanceVersion.unavailable("NOT_RECORDED")),
                List.of(ProvenanceVersion.unavailable("NOT_RECORDED")),
                ProvenanceSource.available("asset-service", "1.0", "abcdef1"),
                ProvenanceVersion.available("evidence", "1.0", null));

        assertThatThrownBy(() -> builder.build(incident(), investigation(), conclusion("summary"), run(),
                metadata(), invalid)).hasMessage("CONTRACT_VALIDATION_FAILED");
    }

    private Incident incident() {
        return new Incident(10L, "asset-service", "prod", "1.0", "abcdef1", "symptom", now());
    }

    private Investigation investigation() {
        return new Investigation(20L, 10L, InvestigationStatus.COMPLETED, 30L,
                30, 60, 1800, 5, now(), now());
    }

    private Conclusion conclusion(String summary) {
        return new Conclusion(40L, 20L, "ROOT_CAUSE_FOUND", "Asset.insert", "rollback",
                "1,2", null, summary, now());
    }

    private InvestigationRun run() {
        return new InvestigationRun(30L, 20L, null, null, null, now(), now());
    }

    private DiagnosisEventBuildMetadata metadata() {
        return new DiagnosisEventBuildMetadata("0d88ca17-936c-4ac0-9cff-2a3bf8e4ee08",
                OffsetDateTime.of(now(), ZoneOffset.UTC), "dpom-agent-test-01", 1);
    }

    private DiagnosisEventProvenance provenance() {
        ProvenanceVersion unavailable = ProvenanceVersion.unavailable("NOT_RECORDED");
        return new DiagnosisEventProvenance(ProvenanceVersion.available("DPOMAgent", "1.0", null),
                unavailable, unavailable, List.of(unavailable), List.of(unavailable),
                ProvenanceSource.available("asset-service", "1.0", "abcdef1"),
                ProvenanceVersion.available("diagnostic-evidence-package", "1.0", null));
    }

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 8, 21, 14, 30);
    }
}
