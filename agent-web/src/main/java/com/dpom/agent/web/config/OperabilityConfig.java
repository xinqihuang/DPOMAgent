package com.dpom.agent.web.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;

/**
 * 可观测性共享 Bean：Clock（可注入替换以便测试）+ 执行器 gauge。
 */
@Configuration
public class OperabilityConfig {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Object executorMetrics(@Qualifier("investigationExecutor") ThreadPoolTaskExecutor executor,
            MeterRegistry registry) {
        try {
            Gauge.builder("dpom.executor.queue.size", executor, e -> e.getThreadPoolExecutor().getQueue().size())
                    .description("调查执行器队列深度").register(registry);
            Gauge.builder("dpom.executor.active", executor, ThreadPoolTaskExecutor::getActiveCount)
                    .description("调查执行器活跃线程").register(registry);
            Gauge.builder("dpom.executor.pool.size", executor, ThreadPoolTaskExecutor::getPoolSize)
                    .description("调查执行器线程池大小").register(registry);
            Gauge.builder("dpom.executor.queue.capacity", executor,
                            e -> e.getThreadPoolExecutor().getQueue().size()
                                    + e.getThreadPoolExecutor().getQueue().remainingCapacity())
                    .description("调查执行器队列容量").register(registry);
        } catch (RuntimeException ignored) {
            // 运行期 register 为 best-effort，不阻断启动
        }
        return new Object();
    }
}
