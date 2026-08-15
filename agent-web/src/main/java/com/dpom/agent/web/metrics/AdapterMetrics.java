package com.dpom.agent.web.metrics;

import com.dpom.agent.web.health.AdapterHealthRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 适配器计时共享逻辑：真实 delegate 调用是主语义；metrics/被动 health 全部 best-effort。
 * 指标失败不阻止调用、不改变返回值、不替换 delegate 原异常。
 */
@Component
public class AdapterMetrics {

    private static final String ADAPTER_DURATION = "dpom.adapter.call.duration";

    private final MeterRegistry registry;
    private final AdapterHealthRegistry health;

    public AdapterMetrics(MeterRegistry registry, AdapterHealthRegistry health) {
        this.registry = registry;
        this.health = health;
    }

    /** 计时并记录一次适配器调用；delegate 返回值/异常原样透传，指标失败被吞掉。healthAdapter 为 null 表示不参与被动健康观测。 */
    public <T> T record(String adapter, AdapterHealthRegistry.Adapter healthAdapter, Supplier<T> call) {
        Timer.Sample sample = startSample();
        try {
            T result = call.get();
            stop(sample, adapter, ErrorCodes.NONE, healthAdapter);
            return result;
        } catch (RuntimeException e) {
            stop(sample, adapter, ErrorCodes.adapter(e), healthAdapter);
            throw e;
        }
    }

    private Timer.Sample startSample() {
        try {
            return Timer.start(registry);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void stop(Timer.Sample sample, String adapter, String errorCode,
            AdapterHealthRegistry.Adapter healthAdapter) {
        try {
            if (sample != null) {
                sample.stop(Timer.builder(ADAPTER_DURATION)
                        .tag("adapter", adapter)
                        .tag("errorCode", errorCode)
                        .register(registry));
            }
        } catch (RuntimeException ignored) {
            // best-effort，不改变 delegate 结果/异常
        }
        try {
            if (healthAdapter != null) {
                health.record(healthAdapter, ErrorCodes.NONE.equals(errorCode));
            }
        } catch (RuntimeException ignored) {
            // best-effort，不改变 delegate 结果/异常
        }
    }
}
