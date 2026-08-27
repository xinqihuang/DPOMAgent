package com.dpom.agent.core.diagnosisevent;

import com.fasterxml.jackson.databind.JsonNode;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Diagnosis Event v1 的稳定错误契约验证器。
 */
public final class DiagnosisEventContractValidator {

    private static final Set<String> TOP_LEVEL = Set.of("eventId", "eventType", "schemaVersion", "occurredAt",
            "producer", "incidentId", "investigationId", "runId", "aggregateSequence", "idempotencyKey",
            "provenance", "inlinePayload", "artifactRef");
    private static final Set<String> FORBIDDEN_CONTENT_FIELDS = Set.of("topic", "sdktype", "accesskey", "secretkey",
            "password", "token", "objectkey");
    private static final Set<String> UNAVAILABLE_REASONS = Set.of("NOT_APPLICABLE", "NOT_RECORDED", "LEGACY_SOURCE",
            "PROVIDER_UNAVAILABLE");

    private final CanonicalJsonWriter writer;

    /**
     * 创建验证器。
     *
     * @param writer RFC 8785 写入器
     */
    public DiagnosisEventContractValidator(CanonicalJsonWriter writer) {
        this.writer = writer;
    }

    /**
     * 验证并规范化事件。
     *
     * @param event JSON 事件
     * @return 规范内容与摘要
     */
    public ValidatedDiagnosisEvent validate(JsonNode event) {
        validateEnvelope(event);
        validateProducer(event.path("producer"));
        validateProvenance(event.path("provenance"));
        validatePayload(event);
        byte[] canonical = writer.write(event);
        require(canonical.length <= DiagnosisEventBuilder.MAX_EVENT_BYTES, "PAYLOAD_TOO_LARGE");
        return new ValidatedDiagnosisEvent(canonical, sha256(canonical));
    }

    /**
     * 验证同一幂等键是否仍为相同规范内容。
     *
     * @param event        待验证事件
     * @param existingHash 已存内容摘要
     * @return 规范内容与摘要
     */
    public ValidatedDiagnosisEvent validateAgainstExisting(JsonNode event, String existingHash) {
        ValidatedDiagnosisEvent validated = validate(event);
        require(validated.canonicalSha256().equals(existingHash), "IDEMPOTENCY_CONFLICT");
        return validated;
    }

    private void validateEnvelope(JsonNode event) {
        require(event != null && event.isObject(), "CONTRACT_VALIDATION_FAILED");
        require(fieldNames(event).stream().allMatch(TOP_LEVEL::contains), "CONTRACT_VALIDATION_FAILED");
        require(validUuid(text(event, "eventId")), "CONTRACT_VALIDATION_FAILED");
        require(List.of("investigation.completed", "investigation.replay-ready")
                .contains(text(event, "eventType")), "CONTRACT_VALIDATION_FAILED");
        String schema = text(event, "schemaVersion");
        require(schema != null && schema.matches("1\\.[0-9]+"),
                schema != null && schema.matches("[2-9][0-9]*\\.[0-9]+")
                        ? "UNSUPPORTED_SCHEMA" : "CONTRACT_VALIDATION_FAILED");
        require(validTime(text(event, "occurredAt")), "CONTRACT_VALIDATION_FAILED");
        require(validIdentifier(text(event, "incidentId"), 128), "CONTRACT_VALIDATION_FAILED");
        require(validIdentifier(text(event, "investigationId"), 128), "CONTRACT_VALIDATION_FAILED");
        require(validIdentifier(text(event, "runId"), 128), "CONTRACT_VALIDATION_FAILED");
        JsonNode sequence = event.path("aggregateSequence");
        require(sequence.isIntegralNumber() && sequence.canConvertToLong() && sequence.longValue() >= 1,
                "CONTRACT_VALIDATION_FAILED");
        require(validIdentifier(text(event, "idempotencyKey"), 200), "CONTRACT_VALIDATION_FAILED");
    }

    private void validateProducer(JsonNode producer) {
        require(objectFields(producer, Set.of("service", "instanceId")), "CONTRACT_VALIDATION_FAILED");
        require("DPOMAgent".equals(text(producer, "service")), "CONTRACT_VALIDATION_FAILED");
        require(validIdentifier(text(producer, "instanceId"), 128), "CONTRACT_VALIDATION_FAILED");
    }

    private void validateProvenance(JsonNode provenance) {
        Set<String> fields = Set.of("application", "model", "prompt", "skills", "toolContracts", "source",
                "evidenceSchema");
        require(objectFields(provenance, fields) && fieldNames(provenance).containsAll(fields),
                "CONTRACT_VALIDATION_FAILED");
        validateVersion(provenance.path("application"));
        validateVersion(provenance.path("model"));
        validateVersion(provenance.path("prompt"));
        validateVersionArray(provenance.path("skills"), 64);
        validateVersionArray(provenance.path("toolContracts"), 128);
        validateSource(provenance.path("source"));
        validateVersion(provenance.path("evidenceSchema"));
    }

    private void validateVersionArray(JsonNode array, int maxSize) {
        require(array.isArray() && !array.isEmpty() && array.size() <= maxSize, "CONTRACT_VALIDATION_FAILED");
        array.forEach(this::validateVersion);
    }

    private void validateVersion(JsonNode version) {
        String status = text(version, "status");
        if ("available".equals(status)) {
            require(objectFields(version, Set.of("status", "name", "version", "provider")),
                    "CONTRACT_VALIDATION_FAILED");
            require(nonBlank(text(version, "name")) && nonBlank(text(version, "version")),
                    "CONTRACT_VALIDATION_FAILED");
        } else if ("unavailable".equals(status)) {
            require(objectFields(version, Set.of("status", "reasonCode")), "CONTRACT_VALIDATION_FAILED");
            require(UNAVAILABLE_REASONS.contains(text(version, "reasonCode")), "CONTRACT_VALIDATION_FAILED");
        } else {
            throw failure("CONTRACT_VALIDATION_FAILED");
        }
    }

    private void validateSource(JsonNode source) {
        String status = text(source, "status");
        if ("available".equals(status)) {
            require(objectFields(source, Set.of("status", "serviceCode", "release", "commitSha")),
                    "CONTRACT_VALIDATION_FAILED");
            require(validIdentifier(text(source, "serviceCode"), 128), "CONTRACT_VALIDATION_FAILED");
            require(nonBlank(text(source, "release")) || validCommit(text(source, "commitSha")),
                    "CONTRACT_VALIDATION_FAILED");
        } else if ("unavailable".equals(status)) {
            require(objectFields(source, Set.of("status", "reasonCode")), "CONTRACT_VALIDATION_FAILED");
            require(UNAVAILABLE_REASONS.contains(text(source, "reasonCode")), "CONTRACT_VALIDATION_FAILED");
        } else {
            throw failure("CONTRACT_VALIDATION_FAILED");
        }
    }

    private void validatePayload(JsonNode event) {
        boolean inline = event.has("inlinePayload");
        boolean artifact = event.has("artifactRef");
        require(inline != artifact, "CONTRACT_VALIDATION_FAILED");
        if (inline) {
            validateInline(event.path("inlinePayload"));
        } else {
            validateArtifact(event.path("artifactRef"));
        }
    }

    private void validateInline(JsonNode payload) {
        require(objectFields(payload, Set.of("payloadType", "payloadSchemaVersion", "content")),
                "CONTRACT_VALIDATION_FAILED");
        require(List.of("diagnosis-summary", "replay-manifest").contains(text(payload, "payloadType")),
                "CONTRACT_VALIDATION_FAILED");
        require(versionOne(text(payload, "payloadSchemaVersion")), "CONTRACT_VALIDATION_FAILED");
        JsonNode content = payload.path("content");
        require(content.isObject() && !content.isEmpty() && content.size() <= 64, "CONTRACT_VALIDATION_FAILED");
        rejectForbiddenContent(content);
        require(writer.write(payload).length <= DiagnosisEventBuilder.MAX_INLINE_PAYLOAD_BYTES, "PAYLOAD_TOO_LARGE");
    }

    private void rejectForbiddenContent(JsonNode value) {
        if (value.isObject()) {
            value.fields().forEachRemaining(entry -> {
                require(!FORBIDDEN_CONTENT_FIELDS.contains(entry.getKey().toLowerCase()),
                        "SECURITY_BOUNDARY_VIOLATION");
                rejectForbiddenContent(entry.getValue());
            });
        } else if (value.isArray()) {
            value.forEach(this::rejectForbiddenContent);
        }
    }

    private void validateArtifact(JsonNode artifact) {
        Set<String> fields = Set.of("artifactId", "locationType", "locator", "mediaType", "byteSize", "sha256",
                "artifactSchemaVersion", "retentionClass", "createdAt");
        require(objectFields(artifact, fields) && fieldNames(artifact).containsAll(fields),
                "CONTRACT_VALIDATION_FAILED");
        require(validIdentifier(text(artifact, "artifactId"), 128), "CONTRACT_VALIDATION_FAILED");
        require(List.of("controlled-obs-evidence", "evaluation-artifact-store", "local-conformance-fixture")
                .contains(text(artifact, "locationType")), "CONTRACT_VALIDATION_FAILED");
        String locator = text(artifact, "locator");
        require(locator != null && locator.matches("(?![A-Za-z]:)(?!.*\\.\\.)(?!.*\\\\)[A-Za-z0-9][A-Za-z0-9._:/-]*"),
                "SECURITY_BOUNDARY_VIOLATION");
        require(nonBlank(text(artifact, "mediaType")), "CONTRACT_VALIDATION_FAILED");
        JsonNode size = artifact.path("byteSize");
        require(size.isIntegralNumber() && size.longValue() >= 1 && size.longValue() <= 52_428_800,
                "CONTRACT_VALIDATION_FAILED");
        require(text(artifact, "sha256") != null && text(artifact, "sha256").matches("[0-9a-f]{64}"),
                "CONTRACT_VALIDATION_FAILED");
        require(versionOne(text(artifact, "artifactSchemaVersion")), "CONTRACT_VALIDATION_FAILED");
        require(List.of("TRANSIENT_7D", "EVAL_90D", "GOLD_LONG_TERM").contains(text(artifact, "retentionClass")),
                "CONTRACT_VALIDATION_FAILED");
        require(validTime(text(artifact, "createdAt")), "CONTRACT_VALIDATION_FAILED");
    }

    private boolean objectFields(JsonNode node, Set<String> allowed) {
        return node.isObject() && fieldNames(node).stream().allMatch(allowed::contains);
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new java.util.HashSet<>();
        Iterator<String> fields = node.fieldNames();
        fields.forEachRemaining(names::add);
        return names;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private boolean validIdentifier(String value, int maxLength) {
        return nonBlank(value) && value.length() <= maxLength
                && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*");
    }

    private boolean validCommit(String value) {
        return value != null && value.matches("[0-9a-fA-F]{7,64}");
    }

    private boolean validUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    private boolean validTime(String value) {
        try {
            OffsetDateTime.parse(value);
            return true;
        } catch (DateTimeParseException | NullPointerException e) {
            return false;
        }
    }

    private boolean versionOne(String value) {
        return value != null && value.matches("1\\.[0-9]+");
    }

    private boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void require(boolean valid, String errorCode) {
        if (!valid) {
            throw failure(errorCode);
        }
    }

    private DiagnosisEventValidationException failure(String errorCode) {
        return new DiagnosisEventValidationException(errorCode);
    }
}
