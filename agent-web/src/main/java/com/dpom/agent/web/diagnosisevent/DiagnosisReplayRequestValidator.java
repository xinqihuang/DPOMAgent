package com.dpom.agent.web.diagnosisevent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.UUID;

/**
 * 对重放请求执行字段 allow-list 和文本边界校验。
 */
public final class DiagnosisReplayRequestValidator {

    private static final Set<String> FIELDS = Set.of("eventId", "operatorRef", "reason");
    private final ObjectMapper objectMapper;
    private final int maxOperatorRef;
    private final int maxReason;

    /** 创建请求校验器。 */
    public DiagnosisReplayRequestValidator(ObjectMapper objectMapper, int maxOperatorRef, int maxReason) {
        this.objectMapper = objectMapper;
        this.maxOperatorRef = maxOperatorRef;
        this.maxReason = maxReason;
    }

    /** 解析并严格校验请求。 */
    public DiagnosisReplayRequest validate(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            require(root != null && root.isObject() && validFields(root));
            String eventId = text(root, "eventId");
            String operatorRef = text(root, "operatorRef");
            String reason = text(root, "reason");
            UUID.fromString(eventId);
            require(operatorRef.length() <= maxOperatorRef
                    && operatorRef.matches("[A-Za-z0-9][A-Za-z0-9._:@-]*"));
            require(reason.length() <= maxReason && !reason.isBlank() && reason.equals(reason.strip()));
            return new DiagnosisReplayRequest(eventId, operatorRef, reason);
        } catch (RuntimeException | java.io.IOException exception) {
            throw new IllegalArgumentException("INVALID_REPLAY_REQUEST");
        }
    }

    private boolean validFields(JsonNode root) {
        Set<String> actual = new java.util.HashSet<>();
        root.fieldNames().forEachRemaining(actual::add);
        return actual.equals(FIELDS);
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        require(value != null && value.isTextual());
        return value.textValue();
    }

    private void require(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("INVALID_REPLAY_REQUEST");
        }
    }
}
