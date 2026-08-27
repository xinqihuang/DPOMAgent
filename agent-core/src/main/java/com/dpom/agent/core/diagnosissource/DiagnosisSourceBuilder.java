package com.dpom.agent.core.diagnosissource;

import com.dpom.agent.core.authority.AuthorityId;
import com.dpom.agent.core.authority.DiagnosisSourceProjection;
import com.dpom.agent.core.authority.InvestigationAuthority;
import com.dpom.agent.core.diagnosisevent.Rfc8785CanonicalJsonWriter;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** 从终态权威快照构建规范化 diagnosis-source/v1。 */
@Component
public class DiagnosisSourceBuilder {

    /** 当前诊断源契约版本。 */
    public static final String CONTRACT_VERSION = "diagnosis-source/v1";

    private final ObjectMapper objectMapper;
    private final Rfc8785CanonicalJsonWriter canonicalJsonWriter;

    /** 创建构建器。 */
    public DiagnosisSourceBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.canonicalJsonWriter = new Rfc8785CanonicalJsonWriter(objectMapper);
    }

    /** 构建带规范摘要的不可变终态源。 */
    public DiagnosisSourceProjection build(InvestigationAuthority.Snapshot snapshot) {
        validateTerminal(snapshot);
        InvestigationAuthority.ConclusionState conclusion = snapshot.conclusion();
        InvestigationAuthority.RunState run = snapshot.runs().stream()
                .filter(candidate -> candidate.id().equals(snapshot.currentRunId()))
                .findFirst().orElseThrow(() -> new IllegalStateException("DIAGNOSIS_SOURCE_RUN_MISSING"));
        AuthorityId sourceId = AuthorityId.derive("diagnosis-source", snapshot.investigationId().value(),
                Long.toString(snapshot.version()));
        List<DiagnosisSourceProjection.SupportingObservation> observations = conclusion
                .supportingObservationIds().stream()
                .map(id -> observation(snapshot, id))
                .sorted(Comparator.comparing(item -> item.observationId().value()))
                .toList();
        List<String> alternatives = conclusion.alternatives().stream().distinct().sorted().toList();
        List<String> gaps = conclusion.evidenceGaps().stream().distinct().sorted().toList();
        List<DiagnosisSourceProjection.ComponentProvenance> provenance = provenance(run);
        UnsignedSource unsigned = new UnsignedSource(sourceId, CONTRACT_VERSION, snapshot.investigationId(),
                snapshot.incident().id(), snapshot.version(), snapshot.status(), run.id(), conclusion.id(),
                conclusion.disposition(), conclusion.rootCause(), observations, alternatives, gaps,
                provenance, conclusion.createdAt());
        String digest = sha256(canonicalJsonWriter.write(objectMapper.valueToTree(unsigned)));
        return new DiagnosisSourceProjection(sourceId, CONTRACT_VERSION, snapshot.investigationId(),
                snapshot.incident().id(), snapshot.version(), snapshot.status(), run.id(), conclusion.id(),
                conclusion.disposition(), conclusion.rootCause(), observations, alternatives, gaps,
                provenance, conclusion.createdAt(), digest);
    }

    private static DiagnosisSourceProjection.SupportingObservation observation(
            InvestigationAuthority.Snapshot snapshot, AuthorityId id) {
        InvestigationAuthority.ObservationState value = snapshot.observations().stream()
                .filter(candidate -> candidate.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("DIAGNOSIS_SOURCE_OBSERVATION_MISSING"));
        return new DiagnosisSourceProjection.SupportingObservation(value.id(), value.source(),
                value.evidenceReference(), value.evidenceSha256(), value.summary());
    }

    private static List<DiagnosisSourceProjection.ComponentProvenance> provenance(
            InvestigationAuthority.RunState run) {
        return List.of(
                new DiagnosisSourceProjection.ComponentProvenance("DPOMAgent", "authority-v1"),
                new DiagnosisSourceProjection.ComponentProvenance("model", run.modelVersion()),
                new DiagnosisSourceProjection.ComponentProvenance("prompt", run.promptVersion()),
                new DiagnosisSourceProjection.ComponentProvenance("toolset", run.toolsetVersion()))
                .stream().sorted(Comparator.comparing(DiagnosisSourceProjection.ComponentProvenance::componentId))
                .toList();
    }

    private static void validateTerminal(InvestigationAuthority.Snapshot snapshot) {
        boolean terminal = snapshot.status() == InvestigationStatus.COMPLETED
                || snapshot.status() == InvestigationStatus.INCONCLUSIVE;
        if (!terminal || snapshot.conclusion() == null) {
            throw new IllegalStateException("DIAGNOSIS_SOURCE_TERMINAL_REQUIRED");
        }
        if (snapshot.currentRunId() == null) {
            throw new IllegalStateException("DIAGNOSIS_SOURCE_RUN_MISSING");
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA256_UNAVAILABLE", e);
        }
    }

    private record UnsignedSource(AuthorityId sourceId, String contractVersion, AuthorityId investigationId,
                                  AuthorityId incidentId, long aggregateVersion, InvestigationStatus status,
                                  AuthorityId runId, AuthorityId conclusionId,
                                  InvestigationAuthority.ConclusionDisposition disposition,
                                  String rootCause,
                                  List<DiagnosisSourceProjection.SupportingObservation> supportingObservations,
                                  List<String> alternatives, List<String> evidenceGaps,
                                  List<DiagnosisSourceProjection.ComponentProvenance> provenance,
                                  java.time.Instant committedAt) {
    }
}

