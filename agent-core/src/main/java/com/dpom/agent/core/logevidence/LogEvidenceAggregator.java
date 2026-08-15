package com.dpom.agent.core.logevidence;

import com.dpom.agent.common.logtemplate.LogParameter;
import com.dpom.agent.common.logtemplate.LogParseResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 日志证据聚合器：把 Drain3 的逐行解析结果按簇聚合为带身份、严重级别、时间范围、代表样本与脱敏参数分布的 LogEvidence。
 */
public class LogEvidenceAggregator {

    private static final String SOURCE = "drain3";
    private static final List<String> SENSITIVE_MASKS = List.of(
            "password", "token", "secret", "auth", "apikey", "api_key", "deviceid", "tenantid", "userid",
            "account", "email", "phone", "ip", "card", "ssn");
    private final LogRedactor redactor = new LogRedactor();

    /**
     * 聚合并转换为日志证据。
     *
     * @param logs         结构化日志行（与 results 按下标对齐）
     * @param results      逐行模板解析结果
     * @param service      服务编码
     * @param environment  环境
     * @param release      发布版本
     * @param commit       提交 SHA
     * @param timeRange    时间范围
     * @param minerVersion 挖掘器版本
     * @param limits       摄入上限（用于样本/参数截断）
     * @return 按簇聚合的日志证据列表
     */
    public List<LogEvidence> aggregate(List<StructuredLog> logs, List<LogParseResult> results, String service,
                                       String environment, String release, String commit, String timeRange,
                                       String minerVersion, LogIntakeLimits limits) {
        if (logs.size() != results.size()) {
            throw new IllegalArgumentException("日志行数与解析结果数不一致");
        }
        Map<Integer, ClusterAcc> accs = new LinkedHashMap<>();
        for (int i = 0; i < logs.size(); i++) {
            StructuredLog s = logs.get(i);
            LogParseResult r = results.get(i);
            accs.computeIfAbsent(r.clusterId(), id -> new ClusterAcc(id, r.template())).add(s, r);
        }
        List<LogEvidence> out = new ArrayList<>();
        int seq = 0;
        for (ClusterAcc acc : accs.values()) {
            out.add(acc.toEvidence("ev-" + (++seq), service, environment, release, commit, timeRange, minerVersion,
                    limits));
        }
        return out;
    }

    /**
     * 判断参数掩码是否属于敏感字段。
     */
    private static boolean sensitiveMask(String mask) {
        if (mask == null) {
            return false;
        }
        String m = mask.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        return SENSITIVE_MASKS.stream().anyMatch(m::contains);
    }

    /**
     * 单个簇的累加器。
     */
    private final class ClusterAcc {

        private final int clusterId;
        private final String template;
        private int count;
        private String firstSeen = "";
        private String lastSeen = "";
        private final Map<String, Integer> severity = new HashMap<>();
        private final List<String> samples = new ArrayList<>();
        private final Map<String, List<String>> params = new LinkedHashMap<>();

        ClusterAcc(int clusterId, String template) {
            this.clusterId = clusterId;
            this.template = template;
        }

        void add(StructuredLog s, LogParseResult r) {
            count++;
            mergeTime(s.timestamp());
            severity.merge(s.level(), 1, Integer::sum);
            samples.add(redactor.redact(s.message()));
            for (LogParameter p : r.params()) {
                String value = sensitiveMask(p.mask()) ? redactor.stableHash(p.value()) : redactor.redact(p.value());
                params.computeIfAbsent(p.mask(), k -> new ArrayList<>()).add(value);
            }
        }

        void mergeTime(String ts) {
            if (ts == null || ts.isEmpty()) {
                return;
            }
            if (firstSeen.isEmpty() || ts.compareTo(firstSeen) < 0) {
                firstSeen = ts;
            }
            if (lastSeen.isEmpty() || ts.compareTo(lastSeen) > 0) {
                lastSeen = ts;
            }
        }

        LogEvidence toEvidence(String evidenceId, String service, String environment, String release, String commit,
                               String timeRange, String minerVersion, LogIntakeLimits limits) {
            List<String> samplesBounded = new ArrayList<>(samples);
            boolean truncated = samples.size() > limits.maxSamplesPerTemplate();
            if (truncated) {
                samplesBounded = samplesBounded.subList(0, limits.maxSamplesPerTemplate());
            }
            Map<String, List<String>> paramsBounded = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : params.entrySet()) {
                List<String> values = e.getValue();
                paramsBounded.put(e.getKey(),
                        values.size() > limits.maxParamValues() ? values.subList(0, limits.maxParamValues()) : values);
            }
            LogTemplateSummary summary = new LogTemplateSummary(clusterId, template, count, firstSeen, lastSeen,
                    severity, samplesBounded, new ParameterDistribution(paramsBounded), truncated);
            return new LogEvidence(evidenceId, summary, service, environment, release, commit, timeRange, null,
                    minerVersion, new EvidenceProvenance(SOURCE, commit, null, null, minerVersion, null));
        }
    }
}
