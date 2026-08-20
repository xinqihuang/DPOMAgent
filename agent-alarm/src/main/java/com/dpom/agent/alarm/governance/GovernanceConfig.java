package com.dpom.agent.alarm.governance;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 治理层默认实现装配：指纹缓存默认为空操作实现，可被自定义 bean 覆盖（如 Redis）。
 */
@Configuration
public class GovernanceConfig {

    /**
     * 默认空操作指纹缓存。
     *
     * @return 空操作缓存
     */
    @Bean
    @ConditionalOnMissingBean(AlarmFingerprintCache.class)
    public AlarmFingerprintCache noOpAlarmFingerprintCache() {
        return new NoOpAlarmFingerprintCache();
    }
}
