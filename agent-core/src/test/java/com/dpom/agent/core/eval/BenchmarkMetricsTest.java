package com.dpom.agent.core.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Benchmark 指标计算验收。
 */
class BenchmarkMetricsTest {

    private static BenchmarkCaseResult passed(String id, long latency) {
        return new BenchmarkCaseResult(id, true, true, "COMPLETED", "ROOT_CAUSE_FOUND", "X", "X",
                List.of("ev-1"), List.of("code-1"), List.of("X"), 2, latency, List.of());
    }

    private static BenchmarkCaseResult failed(String id) {
        return new BenchmarkCaseResult(id, true, false, "INCONCLUSIVE", "INCONCLUSIVE", null, "X",
                List.of(), List.of(), List.of(), 1, 50, List.of("MISSING_SOURCE"));
    }

    private static BenchmarkCaseResult notExecuted(String id) {
        return new BenchmarkCaseResult(id, false, false, "NOT_EXECUTED", null, null, "X",
                List.of(), List.of(), List.of(), 0, 0, List.of());
    }

    @Test
    void overallPassedRequiresAllExecutedAndPassed() {
        BenchmarkMetrics allPassed = BenchmarkMetrics.compute(List.of(passed("E01", 10), passed("E03", 20), passed("E05", 30)));
        assertThat(allPassed.overallPassed()).isTrue();

        BenchmarkMetrics oneFailed = BenchmarkMetrics.compute(List.of(passed("E01", 10), failed("E03"), passed("E05", 30)));
        assertThat(oneFailed.overallPassed()).isFalse();

        BenchmarkMetrics oneMissing = BenchmarkMetrics.compute(List.of(passed("E01", 10), passed("E03", 20), notExecuted("E05")));
        assertThat(oneMissing.overallPassed()).isFalse();
    }

    @Test
    void ratesAreComputed() {
        BenchmarkMetrics m = BenchmarkMetrics.compute(List.of(passed("E01", 10), failed("E03"), passed("E05", 30)));
        assertThat(m.caseCount()).isEqualTo(3);
        assertThat(m.executedCount()).isEqualTo(3);
        assertThat(m.passedCount()).isEqualTo(2);
        assertThat(m.failedCount()).isEqualTo(1);
        assertThat(m.rootCauseAccuracy()).isEqualTo(2.0 / 3.0);
        assertThat(m.evidenceGroundingRate()).isEqualTo(2.0 / 3.0);
    }
}
