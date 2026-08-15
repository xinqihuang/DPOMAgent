package com.dpom.agent.core.logevidence;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 证据束构建器：确定性排序（源码已验证优先、ERROR 优先、频次降序）并在总字节预算内截断。
 */
public class EvidenceBundleBuilder {

    private final int maxBundleBytes;

    /**
     * 构造构建器。
     *
     * @param maxBundleBytes 总字节预算
     */
    public EvidenceBundleBuilder(int maxBundleBytes) {
        this.maxBundleBytes = maxBundleBytes;
    }

    /**
     * 构建证据束。
     *
     * @param service        服务编码
     * @param environment    环境
     * @param release        发布版本
     * @param commit         提交 SHA
     * @param timeRange      时间范围
     * @param logs           日志证据
     * @param anchors        代码锚点
     * @param codes          代码证据
     * @param degradations   降级标记
     * @param contradictions 矛盾标记
     * @return 有预算、有序的证据束
     */
    public EvidenceBundle build(String service, String environment, String release, String commit, String timeRange,
                                List<LogEvidence> logs, List<CodeAnchor> anchors, List<CodeEvidence> codes,
                                List<String> degradations, List<String> contradictions) {
        List<Object> priority = new ArrayList<>();
        for (CodeEvidence c : codes) {
            if ("VERIFIED".equals(c.status())) {
                priority.add(c);
            }
        }
        List<LogEvidence> orderedLogs = new ArrayList<>(logs);
        orderedLogs.sort(Comparator.comparingInt(EvidenceBundleBuilder::severityRank)
                .thenComparing((a, b) -> Integer.compare(b.summary().count(), a.summary().count())));
        priority.addAll(orderedLogs);
        priority.addAll(anchors);
        for (CodeEvidence c : codes) {
            if (!"VERIFIED".equals(c.status())) {
                priority.add(c);
            }
        }

        List<CodeEvidence> codesOut = new ArrayList<>();
        List<LogEvidence> logsOut = new ArrayList<>();
        List<CodeAnchor> anchorsOut = new ArrayList<>();
        int budget = 0;
        boolean truncated = false;
        for (Object item : priority) {
            int bytes = estimateBytes(item);
            if (budget + bytes > maxBundleBytes) {
                truncated = true;
                break;
            }
            if (item instanceof CodeEvidence c) {
                codesOut.add(c);
            } else if (item instanceof LogEvidence l) {
                logsOut.add(l);
            } else if (item instanceof CodeAnchor a) {
                anchorsOut.add(a);
            }
            budget += bytes;
        }
        return new EvidenceBundle(service, environment, release, commit, timeRange, logsOut, anchorsOut, codesOut,
                degradations, contradictions, truncated);
    }

    /**
     * 严重级别排序权重（越小越优先）。
     */
    private static int severityRank(LogEvidence e) {
        int min = 4;
        for (String level : e.summary().severityDistribution().keySet()) {
            min = Math.min(min, rank(level));
        }
        return min;
    }

    /**
     * 级别排序。
     */
    private static int rank(String level) {
        return switch (level.toUpperCase()) {
            case "FATAL" -> 0;
            case "ERROR" -> 1;
            case "WARN" -> 2;
            case "INFO" -> 3;
            default -> 4;
        };
    }

    /**
     * 估算对象字节数。
     */
    private static int estimateBytes(Object o) {
        return String.valueOf(o).getBytes(StandardCharsets.UTF_8).length;
    }
}
