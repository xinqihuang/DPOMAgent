package com.dpom.agent.core.persistence;

import org.springframework.jdbc.support.KeyHolder;

import java.util.Map;

/**
 * 从 {@link KeyHolder} 稳健提取自增主键的工具。
 *
 * <p>H2 在存在 {@code DEFAULT CURRENT_TIMESTAMP} 列时会返回多个生成列（id + created_at），
 * 而 MySQL 驱动在未指定列时以 {@code GENERATED_KEY} 命名自增列。本工具统一兼容两者。</p>
 */
final class GeneratedKeys {

    private GeneratedKeys() {
    }

    /**
     * 提取自增主键。
     *
     * @param keyHolder 键持有者
     * @return 自增主键
     */
    static long longValue(KeyHolder keyHolder) {
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null) {
            for (Map.Entry<String, Object> entry : keys.entrySet()) {
                if ("id".equalsIgnoreCase(entry.getKey()) && entry.getValue() instanceof Number number) {
                    return number.longValue();
                }
            }
            for (Object value : keys.values()) {
                if (value instanceof Number number) {
                    return number.longValue();
                }
            }
        }
        throw new IllegalStateException("未能从 KeyHolder 提取自增主键");
    }
}
