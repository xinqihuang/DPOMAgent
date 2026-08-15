package com.dpom.agent.web.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外部适配器被动健康注册表：只记录最近一次真实业务调用的成功与否 + 时间戳（固定 adapter 枚举，容量固定）。
 * 不存储 Throwable/message/URL/参数/响应；状态有过期语义（超过 TTL 归 UNKNOWN）。
 */
@Component
public class AdapterHealthRegistry {

    /** 被观测的适配器枚举（固定，低基数）。 */
    public enum Adapter { LLM, CODEGRAPH, DRAIN3, RUNTIME }

    /** 被动观测状态。 */
    public enum State { UP, DOWN, UNKNOWN }

    private final Map<Adapter, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public AdapterHealthRegistry(Clock clock, @Value("${dpom.api.adapter-health-ttl:5m}") Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    /** 记录一次业务调用结果（成功与否 + 当前时间戳）。 */
    public void record(Adapter adapter, boolean success) {
        entries.put(adapter, new Entry(success, clock.instant()));
    }

    /** 读取被动状态：从未调用或超过 TTL → UNKNOWN。 */
    public State state(Adapter adapter) {
        Entry entry = entries.get(adapter);
        if (entry == null || entry.at == null) {
            return State.UNKNOWN;
        }
        if (clock.instant().isAfter(entry.at.plus(ttl))) {
            return State.UNKNOWN;
        }
        return entry.success ? State.UP : State.DOWN;
    }

    /** 快照条目（仅成功标记 + 时间戳，不含敏感信息）。 */
    private record Entry(boolean success, Instant at) {
    }
}
