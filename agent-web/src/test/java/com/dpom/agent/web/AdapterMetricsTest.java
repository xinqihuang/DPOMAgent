package com.dpom.agent.web;

import com.dpom.agent.common.llm.ModelTimeoutException;
import com.dpom.agent.web.health.AdapterHealthRegistry;
import com.dpom.agent.web.metrics.AdapterMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * 适配器计时：adapter + errorCode 标签（无 result 标签）；delegate 主语义，metrics/health 全部 best-effort。
 */
class AdapterMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final AdapterHealthRegistry health = new AdapterHealthRegistry(Clock.systemUTC(), Duration.ofMinutes(5));
    private final AdapterMetrics metrics = new AdapterMetrics(registry, health);

    @Test
    void successRecordsNoneAndHealthUp() {
        String result = metrics.record("llm", AdapterHealthRegistry.Adapter.LLM, () -> "ok");
        assertThat(result).isEqualTo("ok");
        assertThat(timer("llm").count()).isEqualTo(1);
        assertThat(timer("llm").getId().getTag("adapter")).isEqualTo("llm");
        assertThat(timer("llm").getId().getTag("errorCode")).isEqualTo("NONE");
        assertThat(timer("llm").getId().getTag("result")).isNull();
        assertThat(health.state(AdapterHealthRegistry.Adapter.LLM)).isEqualTo(AdapterHealthRegistry.State.UP);
    }

    @Test
    void timeoutRecordsTimeoutAndHealthDown() {
        assertThatThrownBy(() -> metrics.record("llm", AdapterHealthRegistry.Adapter.LLM,
                () -> { throw new ModelTimeoutException("secret boom"); })).isInstanceOf(ModelTimeoutException.class);
        assertThat(timer("llm").count()).isEqualTo(1);
        assertThat(timer("llm").getId().getTag("errorCode")).isEqualTo("TIMEOUT");
        assertThat(health.state(AdapterHealthRegistry.Adapter.LLM)).isEqualTo(AdapterHealthRegistry.State.DOWN);
    }

    @Test
    void runtimeAdapterHasNoHealthButHasTimer() {
        metrics.record("runtime", null, () -> "r");
        assertThat(timer("runtime").count()).isEqualTo(1);
        assertThat(timer("runtime").getId().getTag("adapter")).isEqualTo("runtime");
        assertThat(timer("runtime").getId().getTag("errorCode")).isEqualTo("NONE");
    }

    @Test
    void delegateSuccessStillSucceedsWhenMetricsAndHealthBroken() {
        AdapterHealthRegistry brokenHealth = mock(AdapterHealthRegistry.class);
        doThrow(new RuntimeException("health broken")).when(brokenHealth).record(any(), anyBoolean());
        AdapterMetrics broken = new AdapterMetrics(new ThrowingMeterRegistry(), brokenHealth);
        AtomicInteger calls = new AtomicInteger();

        String result = broken.record("llm", AdapterHealthRegistry.Adapter.LLM, () -> {
            calls.incrementAndGet();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void delegateFailureStillRethrowsOriginalWhenMetricsAndHealthBroken() {
        AdapterHealthRegistry brokenHealth = mock(AdapterHealthRegistry.class);
        doThrow(new RuntimeException("health broken")).when(brokenHealth).record(any(), anyBoolean());
        AdapterMetrics broken = new AdapterMetrics(new ThrowingMeterRegistry(), brokenHealth);
        ModelTimeoutException original = new ModelTimeoutException("secret");

        assertThatThrownBy(() -> broken.record("llm", AdapterHealthRegistry.Adapter.LLM,
                () -> { throw original; })).isSameAs(original);
    }

    private io.micrometer.core.instrument.Timer timer(String adapter) {
        return registry.find("dpom.adapter.call.duration").tag("adapter", adapter).timer();
    }
}
