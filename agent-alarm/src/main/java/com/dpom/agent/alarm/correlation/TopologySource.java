package com.dpom.agent.alarm.correlation;

import java.util.Set;

/**
 * 拓扑邻接源：提供资源间的邻接关系，供确定性关联引擎判断资源是否相邻。
 *
 * <p>初始实现为静态配置（见 {@link StaticTopologySource}），后续可接既有拓扑证据，
 * 不破坏关联引擎的纯函数契约。</p>
 */
public interface TopologySource {

    /**
     * 查询与指定资源邻接的资源集合。
     *
     * @param resourceId 资源标识
     * @return 邻接资源集合（不含自身；无邻接返回空集）
     */
    Set<String> adjacentResources(String resourceId);

    /**
     * 判断两个资源是否邻接（相同资源视为邻接）。
     *
     * @param a 资源 a
     * @param b 资源 b
     * @return 邻接返回 true
     */
    boolean isAdjacent(String a, String b);
}
