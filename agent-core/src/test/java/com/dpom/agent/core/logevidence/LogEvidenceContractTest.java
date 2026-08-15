package com.dpom.agent.core.logevidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T101 日志证据领域契约验收：DTO 字段、序列化往返，且不包含远端 MCP DTO。
 */
class LogEvidenceContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * LogEvidence 承载身份、时间范围、截断与来源信息，并可序列化往返。
     */
    @Test
    void logEvidenceCarriesIdentityAndTruncation() throws Exception {
        ParameterDistribution dist = new ParameterDistribution(Map.of("deviceId", List.of("h:aaa", "h:bbb")));
        LogTemplateSummary summary = new LogTemplateSummary(7, "device <*> insert failed", 12,
                "2026-08-14T10:00:00Z", "2026-08-14T10:05:00Z",
                Map.of("ERROR", 12), List.of("device 1001 insert failed"), dist, true);
        LogEvidence evidence = new LogEvidence("ev-1", summary, "asset-service", "prod", "1.0.0", "abc123",
                "10m", List.of("trace-1"), "drain3-0.9",
                new EvidenceProvenance("drain3", "abc123", null, null, "v1", "2026-08-14T10:06:00Z"));

        assertThat(evidence.service()).isEqualTo("asset-service");
        assertThat(evidence.environment()).isEqualTo("prod");
        assertThat(evidence.release()).isEqualTo("1.0.0");
        assertThat(evidence.commit()).isEqualTo("abc123");
        assertThat(evidence.timeRange()).isEqualTo("10m");
        assertThat(evidence.summary().truncated()).isTrue();
        assertThat(evidence.summary().parameterDistribution().valuesByMask()).containsKey("deviceId");
        assertThat(evidence.traceIds()).containsExactly("trace-1");

        String json = mapper.writeValueAsString(evidence);
        LogEvidence back = mapper.readValue(json, LogEvidence.class);
        assertThat(back.evidenceId()).isEqualTo("ev-1");
        assertThat(back.summary().clusterId()).isEqualTo(7);
        assertThat(back.summary().template()).isEqualTo("device <*> insert failed");
        assertThat(back.provenance().source()).isEqualTo("drain3");
        assertThat(back.provenance().ruleVersion()).isEqualTo("v1");
    }

    /**
     * 参数分布空值归一为空表，保证序列化稳定。
     */
    @Test
    void parameterDistributionNormalizesNull() {
        assertThat(new ParameterDistribution(null).valuesByMask()).isEmpty();
    }
}
