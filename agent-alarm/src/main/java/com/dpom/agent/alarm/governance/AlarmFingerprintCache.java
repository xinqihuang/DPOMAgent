package com.dpom.agent.alarm.governance;

import java.util.Optional;

/**
 * 告警指纹存在性缓存端口：去重快速路径，权威状态在 MySQL。
 *
 * <p>默认 NoOp 实现；Redis 实现可作为后续优化注入（仅缓存指纹→告警 id，TTL 为去重窗）。</p>
 */
public interface AlarmFingerprintCache {

    /**
     * 查询指纹对应的已有告警 id。
     *
     * @param fingerprint 指纹
     * @return 告警 id（未命中时为空）
     */
    Optional<Long> existingId(String fingerprint);

    /**
     * 记住指纹与告警 id 的映射。
     *
     * @param fingerprint 指纹
     * @param alarmId     告警 id
     */
    void remember(String fingerprint, long alarmId);
}
