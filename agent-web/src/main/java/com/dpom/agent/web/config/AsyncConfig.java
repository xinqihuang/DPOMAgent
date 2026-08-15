package com.dpom.agent.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 有界调查执行器：core=2/max=4/queue=10，队列满抛异常（由上层映射 429/503）。
 *
 * <p>优雅停机由 Spring 生命周期单一 owner 完成：wait-for-tasks + 有界 await，不自定义 SmartLifecycle。</p>
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "investigationExecutor")
    public ThreadPoolTaskExecutor investigationExecutor(
            @Value("${dpom.executor.await-termination-seconds:30}") int awaitTerminationSeconds) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("investigation-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.setStrictEarlyShutdown(true);
        executor.initialize();
        return executor;
    }
}
