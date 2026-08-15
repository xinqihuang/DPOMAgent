package com.dpom.agent.web;

import com.dpom.agent.web.health.AdapterHealthRegistry;
import com.dpom.agent.web.metrics.AdapterMetrics;
import com.dpom.agent.web.metrics.InvestigationMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 低基数护栏：只扫描 dpom.* 自定义 meter，tag key 白名单 + tag value 有限枚举（排除 JVM/HTTP 内置）。
 */
class LowCardinalityLabelTest {

    private static final Set<String> ALLOWED_KEYS = Set.of("status", "resultType", "adapter", "errorCode");
    private static final Map<String, Set<String>> VALUE_ENUMS = Map.of(
            "status", Set.of("COMPLETED", "INCONCLUSIVE", "FAILED", "REJECTED", "WAITING_FOR_HUMAN"),
            "resultType", Set.of("ROOT_CAUSE_FOUND", "INCONCLUSIVE", "INSUFFICIENT_EVIDENCE", "FAILED", "REJECTED", "NONE"),
            "adapter", Set.of("llm", "codegraph", "drain3", "runtime"),
            "errorCode", Set.of("NONE", "TIMEOUT", "PROVIDER_ERROR", "NOT_FOUND", "NOT_READY", "ERROR",
                    "INVALID_ARGUMENT", "ILLEGAL_STATE", "EXECUTION_ERROR", "CAPACITY_FULL",
                    "RECONCILED_AFTER_RESTART"));

    @Test
    void dpomMetersUseOnlyWhitelistedKeysAndEnumValues() {
        MeterRegistry registry = new SimpleMeterRegistry();
        AdapterHealthRegistry health = new AdapterHealthRegistry(Clock.systemUTC(), Duration.ofMinutes(5));
        InvestigationMetrics metrics = new InvestigationMetrics(registry);
        AdapterMetrics adapterMetrics = new AdapterMetrics(registry, health);

        metrics.recordSubmitted();
        metrics.recordTerminated("COMPLETED", "ROOT_CAUSE_FOUND");
        metrics.recordTerminated("INCONCLUSIVE", "INSUFFICIENT_EVIDENCE");
        metrics.recordTerminated("FAILED", "FAILED");
        metrics.recordTerminated("REJECTED", "REJECTED");
        Timer.Sample sample = metrics.startExecution();
        metrics.stopExecution(sample, "COMPLETED", "ROOT_CAUSE_FOUND", "NONE");
        adapterMetrics.record("llm", AdapterHealthRegistry.Adapter.LLM, () -> "x");
        adapterMetrics.record("codegraph", AdapterHealthRegistry.Adapter.CODEGRAPH, () -> "x");
        adapterMetrics.record("drain3", AdapterHealthRegistry.Adapter.DRAIN3, () -> "x");
        adapterMetrics.record("runtime", null, () -> "x");
        Counter.builder("dpom.reconciliation.recovered").register(registry).increment();
        // executor gauges（无标签）
        io.micrometer.core.instrument.Gauge.builder("dpom.executor.queue.size", 0, n -> n).register(registry);

        long dpomMeters = registry.getMeters().stream()
                .filter(m -> m.getId().getName().startsWith("dpom.")).peek(m -> {
                    for (Tag tag : m.getId().getTags()) {
                        assertThat(ALLOWED_KEYS).as("key").contains(tag.getKey());
                        assertThat(VALUE_ENUMS.get(tag.getKey())).as("value for " + tag.getKey())
                                .contains(tag.getValue());
                    }
                }).count();
        assertThat(dpomMeters).isPositive();
    }
}
