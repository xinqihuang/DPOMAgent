package com.dpom.agent.core.diagnosisevent;

import com.dpom.agent.common.diagnosisevent.DiagnosisEvent;
import com.dpom.agent.common.diagnosisevent.DiagnosisEventProducer;
import com.dpom.agent.common.diagnosisevent.DiagnosisEventProvenance;
import com.dpom.agent.common.diagnosisevent.DiagnosisInlinePayload;
import com.dpom.agent.common.diagnosisevent.ProvenanceSource;
import com.dpom.agent.common.diagnosisevent.ProvenanceVersion;
import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationRun;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 从已持久化的调查事实构造 Diagnosis Event v1。
 */
public final class DiagnosisEventBuilder {

    /** 当前受支持的事件契约版本。 */
    public static final String SCHEMA_VERSION = "1.0";
    /** 当前终态事件类型。 */
    public static final String COMPLETED_EVENT_TYPE = "investigation.completed";
    /** 最大规范事件字节数。 */
    public static final int MAX_EVENT_BYTES = 64 * 1024;
    /** 最大规范内联载荷字节数。 */
    public static final int MAX_INLINE_PAYLOAD_BYTES = 16 * 1024;

    private final ObjectMapper objectMapper;
    private final CanonicalJsonWriter canonicalJsonWriter;

    /**
     * 创建事件构造器。
     *
     * @param objectMapper        已配置 Java 时间和忽略空值的映射器
     * @param canonicalJsonWriter RFC 8785 写入器
     */
    public DiagnosisEventBuilder(ObjectMapper objectMapper, CanonicalJsonWriter canonicalJsonWriter) {
        this.objectMapper = objectMapper;
        this.canonicalJsonWriter = canonicalJsonWriter;
    }

    /**
     * 构造、验证并规范化一个终态事件。
     *
     * @param incident      已持久化事件单
     * @param investigation 已持久化调查
     * @param conclusion    已持久化结论
     * @param run           已持久化运行
     * @param metadata      受信任事件元数据
     * @param provenance    已验证来源配置
     * @return 完整事件
     */
    public BuiltDiagnosisEvent build(Incident incident, Investigation investigation, Conclusion conclusion,
                                     InvestigationRun run, DiagnosisEventBuildMetadata metadata,
                                     DiagnosisEventProvenance provenance) {
        validateFacts(incident, investigation, conclusion, run, metadata);
        validateProvenance(provenance);
        DiagnosisInlinePayload payload = buildPayload(conclusion);
        DiagnosisEvent event = new DiagnosisEvent(metadata.eventId(), COMPLETED_EVENT_TYPE, SCHEMA_VERSION,
                metadata.occurredAt(), new DiagnosisEventProducer("DPOMAgent", metadata.producerInstanceId()),
                text(incident.id()), text(investigation.id()), text(run.id()), metadata.aggregateSequence(),
                investigation.id() + ".completed." + metadata.aggregateSequence(), provenance, payload, null);
        return canonicalize(event, payload);
    }

    private DiagnosisInlinePayload buildPayload(Conclusion conclusion) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("conclusionRef", text(conclusion.id()));
        content.put("resultType", conclusion.resultType());
        content.put("summary", conclusion.summary());
        putIfPresent(content, "rootCauseId", conclusion.rootCauseId());
        putIfPresent(content, "rootCause", conclusion.rootCause());
        if (present(conclusion.evidenceIds())) {
            content.put("evidenceRefs", Arrays.stream(conclusion.evidenceIds().split(","))
                    .map(String::trim).filter(value -> !value.isEmpty()).toList());
        }
        return new DiagnosisInlinePayload("diagnosis-summary", SCHEMA_VERSION, content);
    }

    private BuiltDiagnosisEvent canonicalize(DiagnosisEvent event, DiagnosisInlinePayload payload) {
        JsonNode payloadTree = objectMapper.valueToTree(payload);
        byte[] payloadBytes = canonicalJsonWriter.write(payloadTree);
        if (payloadBytes.length > MAX_INLINE_PAYLOAD_BYTES) {
            throw failure("PAYLOAD_TOO_LARGE");
        }
        JsonNode eventTree = objectMapper.valueToTree(event);
        byte[] canonicalBytes = canonicalJsonWriter.write(eventTree);
        if (canonicalBytes.length > MAX_EVENT_BYTES) {
            throw failure("PAYLOAD_TOO_LARGE");
        }
        return new BuiltDiagnosisEvent(event, canonicalBytes, sha256(canonicalBytes));
    }

    private void validateFacts(Incident incident, Investigation investigation, Conclusion conclusion,
                               InvestigationRun run, DiagnosisEventBuildMetadata metadata) {
        require(incident != null && investigation != null && conclusion != null && run != null && metadata != null);
        require(investigation.id() != null && investigation.incidentId() != null && incident.id() != null);
        require(investigation.incidentId().equals(incident.id()));
        require(conclusion.investigationId().equals(investigation.id()));
        require(run.investigationId().equals(investigation.id()));
        require(run.id() != null && conclusion.id() != null && run.endedAt() != null);
        require(investigation.status() == InvestigationStatus.COMPLETED
                || investigation.status() == InvestigationStatus.INCONCLUSIVE);
        require(present(conclusion.resultType()) && present(conclusion.summary()));
        require(metadata.occurredAt() != null && metadata.aggregateSequence() >= 1);
        require(validUuid(metadata.eventId()) && validIdentifier(metadata.producerInstanceId()));
    }

    private void validateProvenance(DiagnosisEventProvenance provenance) {
        require(provenance != null);
        validateVersion(provenance.application());
        validateVersion(provenance.model());
        validateVersion(provenance.prompt());
        validateVersions(provenance.skills(), 64);
        validateVersions(provenance.toolContracts(), 128);
        validateSource(provenance.source());
        validateVersion(provenance.evidenceSchema());
    }

    private void validateVersions(List<ProvenanceVersion> versions, int maxSize) {
        require(versions != null && !versions.isEmpty() && versions.size() <= maxSize);
        versions.forEach(this::validateVersion);
    }

    private void validateVersion(ProvenanceVersion version) {
        require(version != null);
        if ("available".equals(version.status())) {
            require(present(version.name()) && present(version.version()) && version.reasonCode() == null);
        } else if ("unavailable".equals(version.status())) {
            require(validUnavailableReason(version.reasonCode()));
            require(version.name() == null && version.version() == null && version.provider() == null);
        } else {
            throw failure("CONTRACT_VALIDATION_FAILED");
        }
    }

    private void validateSource(ProvenanceSource source) {
        require(source != null);
        if ("available".equals(source.status())) {
            require(validIdentifier(source.serviceCode()));
            require(present(source.release()) || present(source.commitSha()));
            require(source.reasonCode() == null);
        } else if ("unavailable".equals(source.status())) {
            require(validUnavailableReason(source.reasonCode()));
            require(source.serviceCode() == null && source.release() == null && source.commitSha() == null);
        } else {
            throw failure("CONTRACT_VALIDATION_FAILED");
        }
    }

    private boolean validUnavailableReason(String value) {
        return List.of("NOT_APPLICABLE", "NOT_RECORDED", "LEGACY_SOURCE", "PROVIDER_UNAVAILABLE")
                .contains(value);
    }

    private boolean validIdentifier(String value) {
        return present(value) && value.length() <= 128 && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*");
    }

    private boolean validUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    private void putIfPresent(Map<String, Object> content, String key, String value) {
        if (present(value)) {
            content.put(key, value);
        }
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private String text(Long value) {
        return String.valueOf(value);
    }

    private String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void require(boolean valid) {
        if (!valid) {
            throw failure("CONTRACT_VALIDATION_FAILED");
        }
    }

    private DiagnosisEventValidationException failure(String errorCode) {
        return new DiagnosisEventValidationException(errorCode);
    }
}
