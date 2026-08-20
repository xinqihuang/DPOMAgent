package com.dpom.agent.alarm.correlation;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 静态配置拓扑源：邻接关系由运行时配置注入，默认空（仅同资源可关联）。
 *
 * <p>覆盖范围有限，后续可替换为接既有拓扑证据的实现（在 {@link TopologyConfig} 中以
 * {@code @ConditionalOnMissingBean} 覆盖默认 bean）。线程安全通过 volatile 快照保证。</p>
 */
public class StaticTopologySource implements TopologySource {

    private volatile Map<String, Set<String>> adjacency = Collections.emptyMap();

    /**
     * 替换邻接表（双向对称补全）。
     *
     * @param raw 邻接表（单向即可，内部补全对称）
     */
    public void replaceAdjacency(Map<String, Set<String>> raw) {
        this.adjacency = symmetrize(raw);
    }

    @Override
    public Set<String> adjacentResources(String resourceId) {
        if (resourceId == null) {
            return Collections.emptySet();
        }
        Set<String> neighbors = adjacency.get(resourceId);
        return neighbors == null ? Collections.emptySet() : neighbors;
    }

    @Override
    public boolean isAdjacent(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        Set<String> neighbors = adjacency.get(a);
        return neighbors != null && neighbors.contains(b);
    }

    private static Map<String, Set<String>> symmetrize(Map<String, Set<String>> raw) {
        Map<String, Set<String>> map = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : raw.entrySet()) {
            String from = entry.getKey();
            for (String to : entry.getValue()) {
                map.computeIfAbsent(from, k -> new HashSet<>()).add(to);
                map.computeIfAbsent(to, k -> new HashSet<>()).add(from);
            }
        }
        return map;
    }
}
