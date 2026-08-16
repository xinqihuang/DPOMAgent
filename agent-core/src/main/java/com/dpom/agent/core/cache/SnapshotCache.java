package com.dpom.agent.core.cache;

import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 快照解析结果缓存：用 Redis 缓存 CodeGraph 快照解析，避免重复 MCP 调用。
 */
@Service
public class SnapshotCache {

    private static final String PREFIX = "dpom:snapshot:";
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final Logger LOG = LoggerFactory.getLogger(SnapshotCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 构造器注入。
     *
     * @param redis Redis 客户端
     */
    public SnapshotCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 读取缓存。
     *
     * @param serviceCode 服务编码
     * @param commitSha   提交 SHA
     * @return 缓存的快照（可为空）
     */
    public Optional<CodeSnapshot> get(String serviceCode, String commitSha) {
        try {
            String json = redis.opsForValue().get(PREFIX + serviceCode + ":" + commitSha);
            return json == null ? Optional.empty() : Optional.of(mapper.readValue(json, CodeSnapshot.class));
        } catch (Exception e) {
            LOG.warn("快照缓存读取失败：{}", e.toString());
            return Optional.empty();
        }
    }

    /**
     * 写入缓存（失败不影响主流程）。
     *
     * @param snapshot 快照
     */
    public void put(CodeSnapshot snapshot) {
        try {
            redis.opsForValue().set(PREFIX + snapshot.serviceCode() + ":" + snapshot.commitSha(),
                    mapper.writeValueAsString(snapshot), TTL);
        } catch (Exception e) {
            LOG.warn("快照缓存写入失败：{}", e.toString());
        }
    }
}
