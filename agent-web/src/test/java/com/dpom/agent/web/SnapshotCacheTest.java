package com.dpom.agent.web;

import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.core.cache.SnapshotCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 快照缓存验收：用内存 Map 模拟 Redis 的 put/get，不依赖本机 Redis。
 */
class SnapshotCacheTest {

    private final Map<String, String> store = new HashMap<>();
    private SnapshotCache snapshotCache;

    /**
     * 用内存 Map 桩替 StringRedisTemplate，验证 put/get 往返与 JSON 序列化。
     */
    @BeforeEach
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0)));
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(ops).set(anyString(), anyString(), any(Duration.class));
        snapshotCache = new SnapshotCache(redis);
    }

    /**
     * 缓存并读取快照。
     */
    @Test
    void cachesAndRetrievesSnapshot() {
        CodeSnapshot snapshot = new CodeSnapshot(
                "snap-1", "asset-service", "abc123", "/repos/asset-service", SnapshotStatus.READY);
        snapshotCache.put(snapshot);

        Optional<CodeSnapshot> cached = snapshotCache.get("asset-service", "abc123");

        assertThat(cached).isPresent();
        assertThat(cached.get().snapshotId()).isEqualTo("snap-1");
        assertThat(cached.get().workspacePath()).isEqualTo("/repos/asset-service");
        assertThat(cached.get().status()).isEqualTo(SnapshotStatus.READY);
    }

    /**
     * 缺失键返回空。
     */
    @Test
    void returnsEmptyForMissingKey() {
        assertThat(snapshotCache.get("no-such-service", "deadbeef")).isEmpty();
    }
}
