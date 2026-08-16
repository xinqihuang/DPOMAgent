package com.dpom.agent.web;

import com.dpom.agent.core.persistence.HealthCheckMapper;
import com.dpom.agent.web.controller.HealthController;
import com.dpom.agent.web.health.AdapterHealthRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    private final java.util.List<ThreadPoolTaskExecutor> created = new java.util.ArrayList<>();

    @AfterEach
    void tearDown() {
        created.forEach(ThreadPoolTaskExecutor::shutdown);
    }

    @Test
    void healthDownWhenDbDown() {
        HealthCheckMapper mapper = mock(HealthCheckMapper.class);
        when(mapper.ping()).thenThrow(new RuntimeException("db down"));
        Map<String, Object> body = new HealthController(mapper, executor(1, 1, 1), registry()).health();
        assertThat(body.get("status")).isEqualTo("DOWN");
        assertThat(body.get("db")).isEqualTo("DOWN");
        assertThat(body.get("external")).isEqualTo(java.util.Map.of(
                "drain3", "UNKNOWN", "codegraph", "UNKNOWN", "llm", "UNKNOWN"));
    }

    @Test
    void healthDownWhenExecutorSaturated() throws Exception {
        HealthCheckMapper mapper = healthyMapper();
        ThreadPoolTaskExecutor executor = executor(1, 1, 1);
        CountDownLatch block = new CountDownLatch(1);
        executor.execute(() -> await(block));
        executor.execute(() -> await(block));
        awaitActive(executor, 1);
        Map<String, Object> body = new HealthController(mapper, executor, registry()).health();
        assertThat(body.get("status")).isEqualTo("DOWN");
        assertThat(((Map<?, ?>) body.get("executor")).get("available")).isEqualTo(false);
        block.countDown();
    }

    @Test
    void healthUpWhenDbAndCapacityOk() {
        Map<String, Object> body = new HealthController(healthyMapper(), executor(1, 1, 1), registry()).health();
        assertThat(body.get("status")).isEqualTo("UP");
        assertThat(body.get("db")).isEqualTo("UP");
        assertThat(((Map<?, ?>) body.get("executor")).get("available")).isEqualTo(true);
    }

    private HealthCheckMapper healthyMapper() {
        HealthCheckMapper mapper = mock(HealthCheckMapper.class);
        when(mapper.ping()).thenReturn(1);
        return mapper;
    }

    private AdapterHealthRegistry registry() {
        return new AdapterHealthRegistry(Clock.systemUTC(), Duration.ofMinutes(5));
    }

    private ThreadPoolTaskExecutor executor(int core, int max, int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        created.add(executor);
        return executor;
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitActive(ThreadPoolTaskExecutor executor, int expected) throws Exception {
        for (int i = 0; i < 50 && executor.getActiveCount() < expected; i++) {
            Thread.sleep(20);
        }
        assertThat(executor.getActiveCount()).isGreaterThanOrEqualTo(expected);
    }
}
