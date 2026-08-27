package com.dpom.agent.web.metrics;

import com.dpom.agent.core.diagnosisevent.DiagnosisEventMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 仅使用固定状态、结果和稳定错误码标签的投递指标。
 */
@Component
public class MicrometerDiagnosisEventMetrics implements DiagnosisEventMetrics {

    private static final String TRANSITIONS = "dpom.diagnosis.event.transition";
    private final MeterRegistry registry;

    /** 创建指标适配器。 */
    public MicrometerDiagnosisEventMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void record(String state, String result, String errorCode) {
        try {
            Counter.builder(TRANSITIONS).description("Diagnosis Event 状态迁移数")
                    .tag("state", state)
                    .tag("result", result)
                    .tag("errorCode", errorCode == null ? "NONE" : errorCode)
                    .register(registry).increment();
        } catch (RuntimeException ignored) {
            // 指标是 best-effort，不影响投递状态。
        }
    }
}
