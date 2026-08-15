package com.dpom.agent.core.logevidence;

import com.dpom.agent.common.logtemplate.LogParameter;
import com.dpom.agent.common.logtemplate.LogParseResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T103 Drain3 聚合为 LogEvidence 验收。
 */
class LogEvidenceAggregatorTest {

    private final LogEvidenceAggregator aggregator = new LogEvidenceAggregator();

    /**
     * 同模板聚合：count、时间范围、severity、参数分布与身份。
     */
    @Test
    void aggregatesSameTemplateIntoOneEvidence() {
        StructuredLog l1 = new StructuredLog("2026-08-14T10:00:00Z", "ERROR", "com.example.Svc", "device 1001 insert failed");
        StructuredLog l2 = new StructuredLog("2026-08-14T10:02:00Z", "ERROR", "com.example.Svc", "device 1002 insert failed");
        StructuredLog l3 = new StructuredLog("2026-08-14T10:03:00Z", "WARN", "com.example.Other", "timeout retry");

        LogParseResult r1 = new LogParseResult(7, 2, "device <*> insert failed", List.of(new LogParameter("1001", "deviceId")));
        LogParseResult r2 = new LogParseResult(7, 2, "device <*> insert failed", List.of(new LogParameter("1002", "deviceId")));
        LogParseResult r3 = new LogParseResult(8, 1, "timeout retry", List.of());

        List<LogEvidence> out = aggregator.aggregate(
                List.of(l1, l2, l3), List.of(r1, r2, r3),
                "asset-service", "prod", "1.0.0", "abc123", "5m", "drain3-0.9", LogIntakeLimits.defaults());

        assertThat(out).hasSize(2);
        LogEvidence e7 = out.stream().filter(e -> e.summary().clusterId() == 7).findFirst().orElseThrow();
        assertThat(e7.summary().count()).isEqualTo(2);
        assertThat(e7.summary().template()).isEqualTo("device <*> insert failed");
        assertThat(e7.summary().severityDistribution()).containsEntry("ERROR", 2);
        assertThat(e7.summary().firstSeen()).isEqualTo("2026-08-14T10:00:00Z");
        assertThat(e7.summary().lastSeen()).isEqualTo("2026-08-14T10:02:00Z");
        assertThat(e7.summary().parameterDistribution().valuesByMask()).containsKey("deviceId");
        assertThat(e7.commit()).isEqualTo("abc123");
        assertThat(e7.service()).isEqualTo("asset-service");
    }

    /**
     * 敏感参数与样本脱敏：原始值不得出现在证据中。
     */
    @Test
    void redactsSecretsInSamplesAndParams() {
        StructuredLog l = new StructuredLog("", "ERROR", "", "password=secret123 device 1 insert failed");
        LogParseResult r = new LogParseResult(1, 1, "password=<*> device <*> insert failed",
                List.of(new LogParameter("secret123", "password"), new LogParameter("1", "deviceId")));

        List<LogEvidence> out = aggregator.aggregate(
                List.of(l), List.of(r), "s", "prod", "1.0.0", "c", "1h", "drain3-0.9", LogIntakeLimits.defaults());

        LogEvidence e = out.get(0);
        assertThat(e.summary().representativeSamples().get(0)).doesNotContain("secret123");
        List<String> passwordValues = e.summary().parameterDistribution().valuesByMask().get("password");
        List<String> deviceValues = e.summary().parameterDistribution().valuesByMask().get("deviceId");
        assertThat(passwordValues).allMatch(v -> v.startsWith("h:"));
        assertThat(deviceValues).allMatch(v -> v.startsWith("h:"));
    }

    /**
     * 样本数超上限时截断，且不包含原始敏感值。
     */
    @Test
    void boundsSamplesToLimit() {
        List<StructuredLog> logs = new java.util.ArrayList<>();
        List<LogParseResult> results = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            logs.add(new StructuredLog("", "ERROR", "", "device " + i + " insert failed"));
            results.add(new LogParseResult(7, 10, "device <*> insert failed", List.of(new LogParameter(String.valueOf(i), "deviceId"))));
        }
        List<LogEvidence> out = aggregator.aggregate(logs, results, "s", "prod", "1.0.0", "c", "1h", "drain3-0.9",
                new LogIntakeLimits(100, 100000, 1000, 100, 5, 10));

        LogEvidence e = out.get(0);
        assertThat(e.summary().representativeSamples()).hasSize(5);
        assertThat(e.summary().truncated()).isTrue();
        assertThat(e.summary().representativeSamples()).allMatch(s -> !s.contains("secret"));
    }

    /**
     * 行数与解析结果不一致时拒绝，避免错位。
     */
    @Test
    void rejectsMismatchedInput() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> aggregator.aggregate(
                List.of(new StructuredLog("", "ERROR", "", "a")),
                List.of(),
                "s", "prod", "1.0.0", "c", "1h", "drain3-0.9", LogIntakeLimits.defaults())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
