package com.dpom.agent.web;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 执行器 gauge：queue.size/active/pool.size/queue.capacity 反映实时值（镜像 OperabilityConfig 的注册 lambda）。
 */
class ExecutorMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private ThreadPoolTaskExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void gaugesReflectRealtimeValues() throws Exception {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(2);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        registerGauges();

        assertThat(gauge("dpom.executor.queue.capacity")).isEqualTo(2.0);
        assertThat(gauge("dpom.executor.queue.size")).isEqualTo(0.0);
        assertThat(gauge("dpom.executor.active")).isEqualTo(0.0);

        CountDownLatch block = new CountDownLatch(1);
        executor.execute(() -> await(block));
        awaitActive();
        assertThat(gauge("dpom.executor.active")).isEqualTo(1.0);
        assertThat(gauge("dpom.executor.pool.size")).isEqualTo(1.0);
        block.countDown();
    }

    private void registerGauges() {
        Gauge.builder("dpom.executor.queue.size", executor, e -> e.getThreadPoolExecutor().getQueue().size())
                .register(registry);
        Gauge.builder("dpom.executor.active", executor, ThreadPoolTaskExecutor::getActiveCount).register(registry);
        Gauge.builder("dpom.executor.pool.size", executor, ThreadPoolTaskExecutor::getPoolSize).register(registry);
        Gauge.builder("dpom.executor.queue.capacity", executor,
                        e -> e.getThreadPoolExecutor().getQueue().size()
                                + e.getThreadPoolExecutor().getQueue().remainingCapacity())
                .register(registry);
    }

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitActive() throws Exception {
        for (int i = 0; i < 50 && executor.getActiveCount() < 1; i++) {
            Thread.sleep(20);
        }
    }
}
