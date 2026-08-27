package com.dpom.agent.core.authority;

import com.dpom.agent.core.investigation.InvestigationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 由终态 Investigation 事实生成的不可变诊断源，不是展示报告。 */
public record DiagnosisSourceProjection(AuthorityId sourceId, String contractVersion,
                                        AuthorityId investigationId, AuthorityId incidentId,
                                        long aggregateVersion, InvestigationStatus status,
                                        AuthorityId runId, AuthorityId conclusionId,
                                        InvestigationAuthority.ConclusionDisposition disposition,
                                        String rootCause, List<SupportingObservation> supportingObservations,
                                        List<String> alternatives, List<String> evidenceGaps,
                                        List<ComponentProvenance> provenance, Instant committedAt,
                                        String sourceDigest) {

    /** 将全部集合冻结，禁止调用方在提交后改变权威源。 */
    public DiagnosisSourceProjection {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(investigationId, "investigationId");
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(conclusionId, "conclusionId");
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(rootCause, "rootCause");
        supportingObservations = List.copyOf(supportingObservations);
        alternatives = List.copyOf(alternatives);
        evidenceGaps = List.copyOf(evidenceGaps);
        provenance = List.copyOf(provenance);
        Objects.requireNonNull(committedAt, "committedAt");
        Objects.requireNonNull(sourceDigest, "sourceDigest");
    }

    /** 支撑结论的 Observation 与不可变证据指针。 */
    public record SupportingObservation(AuthorityId observationId, String source,
                                        String evidenceReference, String evidenceSha256,
                                        String summary) {
    }

    /** 终态生成所使用组件的精确版本。 */
    public record ComponentProvenance(String componentId, String componentVersion) {
    }
}

