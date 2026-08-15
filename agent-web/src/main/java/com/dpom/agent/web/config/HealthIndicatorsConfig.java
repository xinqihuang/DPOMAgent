package com.dpom.agent.web.config;

import com.dpom.agent.web.health.AdapterHealthRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 健康指示器：executorCapacity（readiness 组成之一）+ 三个被动适配器指示器（不进 liveness/readiness group）。
 */
@Configuration
public class HealthIndicatorsConfig {

    @Bean
    public HealthIndicator executorCapacityHealthIndicator(
            @Qualifier("investigationExecutor") ThreadPoolTaskExecutor executor) {
        return () -> executorAvailable(executor) ? Health.up().build() : Health.down().build();
    }

    @Bean
    public HealthIndicator llmHealthIndicator(AdapterHealthRegistry registry) {
        return adapterHealth(registry, AdapterHealthRegistry.Adapter.LLM);
    }

    @Bean
    public HealthIndicator codegraphHealthIndicator(AdapterHealthRegistry registry) {
        return adapterHealth(registry, AdapterHealthRegistry.Adapter.CODEGRAPH);
    }

    @Bean
    public HealthIndicator drain3HealthIndicator(AdapterHealthRegistry registry) {
        return adapterHealth(registry, AdapterHealthRegistry.Adapter.DRAIN3);
    }

    private HealthIndicator adapterHealth(AdapterHealthRegistry registry, AdapterHealthRegistry.Adapter adapter) {
        return () -> switch (registry.state(adapter)) {
            case UP -> Health.up().build();
            case DOWN -> Health.down().build();
            case UNKNOWN -> Health.unknown().build();
        };
    }

    private boolean executorAvailable(ThreadPoolTaskExecutor executor) {
        return executor.getActiveCount() < executor.getMaxPoolSize()
                || executor.getThreadPoolExecutor().getQueue().remainingCapacity() > 0;
    }
}
