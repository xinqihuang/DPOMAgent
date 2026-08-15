package com.dpom.agent.web;

import com.dpom.agent.web.health.AdapterHealthRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 被动适配器健康注册表：从未调用/成功/失败/过期语义，时间用可注入 Clock。
 */
class AdapterHealthRegistryTest {

    @Test
    void neverCalledIsUnknown() {
        AdapterHealthRegistry registry = new AdapterHealthRegistry(Clock.systemUTC(), Duration.ofMinutes(5));
        assertThat(registry.state(AdapterHealthRegistry.Adapter.LLM)).isEqualTo(AdapterHealthRegistry.State.UNKNOWN);
    }

    @Test
    void successThenFailureThenStale() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AdapterHealthRegistry registry = new AdapterHealthRegistry(clock, Duration.ofMinutes(5));

        registry.record(AdapterHealthRegistry.Adapter.CODEGRAPH, true);
        assertThat(registry.state(AdapterHealthRegistry.Adapter.CODEGRAPH)).isEqualTo(AdapterHealthRegistry.State.UP);

        registry.record(AdapterHealthRegistry.Adapter.CODEGRAPH, false);
        assertThat(registry.state(AdapterHealthRegistry.Adapter.CODEGRAPH)).isEqualTo(AdapterHealthRegistry.State.DOWN);

        clock.advance(Duration.ofMinutes(6));
        assertThat(registry.state(AdapterHealthRegistry.Adapter.CODEGRAPH)).isEqualTo(AdapterHealthRegistry.State.UNKNOWN);
    }

    @Test
    void perAdapterIsolation() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AdapterHealthRegistry registry = new AdapterHealthRegistry(clock, Duration.ofMinutes(5));
        registry.record(AdapterHealthRegistry.Adapter.LLM, true);
        assertThat(registry.state(AdapterHealthRegistry.Adapter.LLM)).isEqualTo(AdapterHealthRegistry.State.UP);
        assertThat(registry.state(AdapterHealthRegistry.Adapter.DRAIN3)).isEqualTo(AdapterHealthRegistry.State.UNKNOWN);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration d) {
            instant = instant.plus(d);
        }

        @Override public Instant instant() { return instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
