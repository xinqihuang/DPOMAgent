package com.dpom.agent.web;

import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.core.cache.SnapshotCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 快照缓存验收：真实 Redis 的 put/get。
 */
@SpringBootTest
class SnapshotCacheTest {

    @Autowired
    private SnapshotCache snapshotCache;

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
