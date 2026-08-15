package com.dpom.agent.core.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * 回归套件指标（机器可读）。
 *
 * @param caseCount             案例总数
 * @param executedCount         已执行数
 * @param passedCount           通过数
 * @param failedCount           失败数
 * @param rootCauseAccuracy     根因准确率
 * @param evidenceGroundingRate 证据落地率（LOG + VERIFIED SOURCE 引用）
 * @param completionRate        完成率（COMPLETED）
 * @param inconclusiveRate      无结论率（INCONCLUSIVE）
 * @param latencyP50            延迟 p50
 * @param latencyP95            延迟 p95
 * @param overallPassed         总体通过（全部 mandatory 执行且通过）
 */
public record BenchmarkMetrics(int caseCount, int executedCount, int passedCount, int failedCount,
                               double rootCauseAccuracy, double evidenceGroundingRate, double completionRate,
                               double inconclusiveRate, long latencyP50, long latencyP95, boolean overallPassed) {

    /**
     * 从案例结果计算指标。
     *
     * @param results 案例结果列表
     * @return 指标
     */
    public static BenchmarkMetrics compute(List<BenchmarkCaseResult> results) {
        int caseCount = results.size();
        int executed = (int) results.stream().filter(BenchmarkCaseResult::executed).count();
        int passed = (int) results.stream().filter(BenchmarkCaseResult::passed).count();
        int failed = executed - passed;
        double rootCauseAccuracy = executed == 0 ? 0.0
                : (double) results.stream().filter(BenchmarkCaseResult::executed).filter(r -> r.actualRootCauseId() != null
                        && r.actualRootCauseId().equals(r.expectedRootCauseId())).count() / executed;
        double evidenceGroundingRate = executed == 0 ? 0.0
                : (double) results.stream().filter(BenchmarkCaseResult::executed)
                        .filter(r -> !r.logEvidenceIds().isEmpty() && !r.sourceEvidenceIds().isEmpty()).count() / executed;
        double completionRate = executed == 0 ? 0.0
                : (double) results.stream().filter(r -> "COMPLETED".equals(r.status())).count() / executed;
        double inconclusiveRate = executed == 0 ? 0.0
                : (double) results.stream().filter(r -> "INCONCLUSIVE".equals(r.status())).count() / executed;
        List<Long> latencies = new ArrayList<>();
        for (BenchmarkCaseResult r : results) {
            if (r.executed()) {
                latencies.add(r.latencyMs());
            }
        }
        latencies.sort(Long::compareTo);
        long p50 = percentile(latencies, 0.50);
        long p95 = percentile(latencies, 0.95);
        boolean overallPassed = executed == caseCount && failed == 0;
        return new BenchmarkMetrics(caseCount, executed, passed, failed, rootCauseAccuracy,
                evidenceGroundingRate, completionRate, inconclusiveRate, p50, p95, overallPassed);
    }

    /**
     * 计算百分位（最近秩法）。
     */
    private static long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
