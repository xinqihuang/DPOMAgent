package com.dpom.agent.alarm.governance;

import java.util.Optional;

/**
 * 空操作指纹缓存：默认实现，不缓存，去重以 MySQL 为权威。
 *
 * <p>由 {@link GovernanceConfig} 以 {@code @ConditionalOnMissingBean} 装配为默认 bean。</p>
 */
public class NoOpAlarmFingerprintCache implements AlarmFingerprintCache {

    @Override
    public Optional<Long> existingId(String fingerprint) {
        return Optional.empty();
    }

    @Override
    public void remember(String fingerprint, long alarmId) {
        // 空操作
    }
}
