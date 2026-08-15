package com.dpom.agent.core.logevidence;

import java.util.List;
import java.util.Map;

/**
 * 脱敏后的参数分布：参数掩码 -> 脱敏取值列表。
 *
 * <p>允许保留稳定 hash 用于同值关联，但不得反向恢复原始敏感值。</p>
 */
public record ParameterDistribution(Map<String, List<String>> valuesByMask) {

    /**
     * 紧凑构造：空值归一为空表。
     */
    public ParameterDistribution {
        valuesByMask = valuesByMask == null ? Map.of() : Map.copyOf(valuesByMask);
    }
}
