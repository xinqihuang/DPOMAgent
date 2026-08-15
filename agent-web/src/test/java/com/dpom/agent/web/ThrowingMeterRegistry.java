package com.dpom.agent.web;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 测试用：任何 meter 创建都抛异常，模拟运行期指标系统故障。
 */
public class ThrowingMeterRegistry extends SimpleMeterRegistry {

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        throw new IllegalStateException("metrics broken");
    }
}
