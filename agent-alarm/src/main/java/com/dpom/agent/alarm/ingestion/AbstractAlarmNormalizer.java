package com.dpom.agent.alarm.ingestion;

import com.dpom.agent.common.alarm.AlarmStatus;
import com.dpom.agent.common.alarm.SeverityLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * 告警标准化器基类：解析原始 JSON 并抽取公共字段，严重度映射由子类提供。
 *
 * <p>JSON 字段为初始假设（id/resource/name/severity/status/occurredAt/service/environment/tags），
 * 待与真实华为云告警事件样例对齐后调整；{@code rawPayload} 始终保留原始全文以确保无损投影。</p>
 */
abstract class AbstractAlarmNormalizer implements AlarmNormalizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public final Optional<NormalizedAlarm> normalize(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(rawPayload);
            String resourceId = text(root, "resource");
            String alarmName = text(root, "name");
            String occurredText = text(root, "occurredAt");
            if (resourceId == null || alarmName == null || occurredText == null) {
                return Optional.empty();
            }
            LocalDateTime occurredAt = LocalDateTime.parse(occurredText);
            SeverityLevel severity = mapSeverity(text(root, "severity"));
            AlarmStatus status = mapStatus(text(root, "status"));
            NormalizedAlarm result = new NormalizedAlarm(source(), text(root, "id"), resourceId, alarmName,
                    severity, status, occurredAt, text(root, "service"), text(root, "environment"),
                    text(root, "tags"), rawPayload);
            return Optional.of(result);
        } catch (DateTimeParseException | com.fasterxml.jackson.core.JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /**
     * 子类提供来源特定的严重度映射。
     *
     * @param raw 原始严重度（可为空）
     * @return 统一严重度
     */
    protected abstract SeverityLevel mapSeverity(String raw);

    private static AlarmStatus mapStatus(String raw) {
        if (raw == null) {
            return AlarmStatus.FIRING;
        }
        return "RESOLVED".equalsIgnoreCase(raw) ? AlarmStatus.RESOLVED : AlarmStatus.FIRING;
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        return child.asText();
    }
}
