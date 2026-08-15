package com.dpom.agent.web.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * 调查业务指标：提交数、终态数（低基数 status/resultType）、执行延迟（status/resultType/errorCode）。
 * 全部 best-effort：任何 MeterRegistry 异常都在本组件内吞掉，绝不向业务抛异常。
 */
@Component
public class InvestigationMetrics {

    private static final String SUBMITTED = "dpom.investigation.submitted";
    private static final String TERMINATED = "dpom.investigation.terminated";
    private static final String DURATION = "dpom.investigation.execution.duration";

    private final MeterRegistry registry;

    public InvestigationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordSubmitted() {
        try {
            Counter.builder(SUBMITTED).description("调查提交数").register(registry).increment();
        } catch (RuntimeException ignored) {
            // best-effort observability，不向业务抛异常
        }
    }

    public void recordTerminated(String status, String resultType) {
        try {
            Counter.builder(TERMINATED).description("调查终态数")
                    .tag("status", status)
                    .tag("resultType", resultType)
                    .register(registry).increment();
        } catch (RuntimeException ignored) {
            // best-effort observability，不向业务抛异常
        }
    }

    /** 开始计时；失败返回 null（调用点可安全忽略）。 */
    public Timer.Sample startExecution() {
        try {
            return Timer.start(registry);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 结束计时；sample 为 null 或 MeterRegistry 失败时静默跳过。 */
    public void stopExecution(Timer.Sample sample, String status, String resultType, String errorCode) {
        if (sample == null) {
            return;
        }
        try {
            sample.stop(Timer.builder(DURATION).description("调查执行延迟")
                    .tag("status", status)
                    .tag("resultType", resultType)
                    .tag("errorCode", errorCode)
                    .register(registry));
        } catch (RuntimeException ignored) {
            // best-effort observability，不向业务抛异常
        }
    }
}
