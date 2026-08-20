package com.dpom.agent.alarm.correlation;

import com.dpom.agent.alarm.domain.Alarm;
import com.dpom.agent.alarm.domain.AlarmIncident;
import com.dpom.agent.common.alarm.AlarmIncidentStatus;
import com.dpom.agent.common.alarm.AlarmStatus;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 确定性告警关联引擎：时间窗 ∩ 拓扑邻接（同服务/同环境）聚合成事件。
 *
 * <p>纯函数式，不调用 LLM、不计算向量相似度、不访问持久化。聚合严重度取成员最高。
 * 同资源始终视为邻接；不同资源经 {@link TopologySource} 判断邻接。</p>
 */
@Service
public class AlarmCorrelationEngine {

    /** 单告警事件关联依据。 */
    public static final String BASIS_SINGLE = "SINGLE";
    /** 多告警聚合事件关联依据。 */
    public static final String BASIS_AGGREGATE = "TIME_WINDOW+TOPOLOGY";

    private final Duration timeWindow;
    private final TopologySource topology;

    /**
     * 构造关联引擎。
     *
     * @param windowMinutes 时间窗分钟数（配置 {@code dpom.alarm.correlation.window-minutes}，默认 10）
     * @param topology      拓扑邻接源
     */
    public AlarmCorrelationEngine(@Value("${dpom.alarm.correlation.window-minutes:10}") long windowMinutes,
            TopologySource topology) {
        this.timeWindow = Duration.ofMinutes(windowMinutes);
        this.topology = topology;
    }

    /**
     * 对告警列表执行确定性关联，产出候选事件列表（每个告警恰属一个事件）。
     *
     * @param alarms 告警列表（已治理）
     * @return 候选事件列表（事件 id 为空，待持久化回填）
     */
    public List<CorrelatedIncident> correlate(List<Alarm> alarms) {
        int n = alarms.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (shouldCluster(alarms.get(i), alarms.get(j))) {
                    union(parent, i, j);
                }
            }
        }
        return collectClusters(alarms, parent);
    }

    private boolean shouldCluster(Alarm a, Alarm b) {
        if (!sameScope(a, b)) {
            return false;
        }
        if (!withinWindow(a, b)) {
            return false;
        }
        return topology.isAdjacent(a.resourceId(), b.resourceId());
    }

    private static boolean sameScope(Alarm a, Alarm b) {
        return java.util.Objects.equals(a.serviceCode(), b.serviceCode())
                && java.util.Objects.equals(a.environment(), b.environment());
    }

    private boolean withinWindow(Alarm a, Alarm b) {
        LocalDateTime ta = a.lastOccurredAt();
        LocalDateTime tb = b.lastOccurredAt();
        if (ta == null || tb == null) {
            return false;
        }
        return Duration.between(ta, tb).abs().compareTo(timeWindow) <= 0;
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[ra] = rb;
        }
    }

    private List<CorrelatedIncident> collectClusters(List<Alarm> alarms, int[] parent) {
        java.util.Map<Integer, List<Integer>> clusters = new java.util.LinkedHashMap<>();
        for (int i = 0; i < alarms.size(); i++) {
            clusters.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        }
        List<CorrelatedIncident> results = new ArrayList<>();
        for (List<Integer> members : clusters.values()) {
            results.add(buildIncident(alarms, members));
        }
        return results;
    }

    private CorrelatedIncident buildIncident(List<Alarm> alarms, List<Integer> memberIndices) {
        List<Alarm> memberAlarms = new ArrayList<>();
        List<Long> memberIds = new ArrayList<>();
        for (int idx : memberIndices) {
            memberAlarms.add(alarms.get(idx));
            memberIds.add(alarms.get(idx).id());
        }
        SeverityLevel severity = maxSeverity(memberAlarms);
        LocalDateTime startedAt = memberAlarms.stream().map(Alarm::lastOccurredAt)
                .filter(java.util.Objects::nonNull).min(LocalDateTime::compareTo).orElse(null);
        LocalDateTime endedAt = allResolved(memberAlarms)
                ? memberAlarms.stream().map(Alarm::lastOccurredAt).filter(java.util.Objects::nonNull)
                        .max(LocalDateTime::compareTo).orElse(null)
                : null;
        Alarm first = memberAlarms.get(0);
        String basis = memberAlarms.size() > 1 ? BASIS_AGGREGATE : BASIS_SINGLE;
        String summary = buildSummary(memberAlarms);
        AlarmIncident incident = new AlarmIncident(null, AlarmIncidentStatus.OPEN, severity,
                first.serviceCode(), first.environment(), basis, summary, startedAt, endedAt,
                false, null, null, null, null, null, null);
        return new CorrelatedIncident(incident, memberIds);
    }

    private static SeverityLevel maxSeverity(List<Alarm> alarms) {
        SeverityLevel max = SeverityLevel.INFO;
        for (Alarm a : alarms) {
            if (a.severity() != null && severityRank(a.severity()) > severityRank(max)) {
                max = a.severity();
            }
        }
        return max;
    }

    private static int severityRank(SeverityLevel s) {
        return switch (s) {
            case INFO -> 0;
            case WARNING -> 1;
            case CRITICAL -> 2;
        };
    }

    private static boolean allResolved(List<Alarm> alarms) {
        for (Alarm a : alarms) {
            if (a.status() != AlarmStatus.RESOLVED) {
                return false;
            }
        }
        return true;
    }

    private static String buildSummary(List<Alarm> alarms) {
        if (alarms.size() == 1) {
            return "告警：" + alarms.get(0).alarmName();
        }
        long resources = alarms.stream().map(Alarm::resourceId).distinct().count();
        return "告警关联事件：" + alarms.size() + " 条告警，涉及资源 " + resources + " 个";
    }
}
