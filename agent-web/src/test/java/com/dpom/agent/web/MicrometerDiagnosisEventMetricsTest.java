package com.dpom.agent.web;

import com.dpom.agent.web.metrics.MicrometerDiagnosisEventMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Diagnosis Event 指标低基数和敏感内容隔离测试。
 */
class MicrometerDiagnosisEventMetricsTest {

    @Test
    void exposesOnlyBoundedStateResultAndErrorTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerDiagnosisEventMetrics metrics = new MicrometerDiagnosisEventMetrics(registry);

        metrics.record("DEAD", "RETRY_EXHAUSTED", "HTTP_500");

        var meter = registry.get("dpom.diagnosis.event.transition").counter();
        assertThat(meter.getId().getTags()).extracting("key")
                .containsExactlyInAnyOrder("state", "result", "errorCode");
        assertThat(meter.getId().toString()).doesNotContain(
                "investigationId", "incidentId", "canonical", "evidence", "secret");
    }

    @Test
    void registryFailureNeverEscapesBusinessBoundary() {
        MicrometerDiagnosisEventMetrics metrics = new MicrometerDiagnosisEventMetrics(null);
        metrics.record("PENDING", "RETRYABLE_FAILURE", "DELIVERY_IO_ERROR");
    }
}
