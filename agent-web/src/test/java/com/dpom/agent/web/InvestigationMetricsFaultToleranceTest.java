package com.dpom.agent.web;

import com.dpom.agent.web.metrics.InvestigationMetrics;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * InvestigationMetrics 容错：运行期 MeterRegistry 故障不向业务抛异常（register/increment/record 均 best-effort）。
 */
class InvestigationMetricsFaultToleranceTest {

    @Test
    void runtimeRegistryFailureDoesNotThrow() {
        InvestigationMetrics metrics = new InvestigationMetrics(new ThrowingMeterRegistry());
        assertThatCode(() -> {
            metrics.recordSubmitted();
            metrics.recordTerminated("COMPLETED", "ROOT_CAUSE_FOUND");
            Timer.Sample sample = metrics.startExecution();
            metrics.stopExecution(sample, "COMPLETED", "ROOT_CAUSE_FOUND", "NONE");
        }).doesNotThrowAnyException();
    }
}
