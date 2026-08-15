package com.dpom.agent.web;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 优雅停机（executor 生命周期参数）：有界 drain 完成在途任务、不无限等待、重复 shutdown 幂等（无 double shutdown）。
 */
class GracefulShutdownTest {

    @Test
    void boundedDrainCompletesInFlightTasks() throws Exception {
        ThreadPoolTaskExecutor executor = executor();
        CountDownLatch done = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
        long t0 = System.currentTimeMillis();
        executor.shutdown();
        long elapsed = System.currentTimeMillis() - t0;
        assertThat(elapsed).isLessThan(3000);
        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void shutdownIsIdempotent() {
        ThreadPoolTaskExecutor executor = executor();
        executor.execute(() -> { });
        executor.shutdown();
        executor.shutdown();
        executor.shutdown();
    }

    @Test
    void shutdownStopsAcceptingNewTasks() {
        ThreadPoolTaskExecutor executor = executor();
        executor.shutdown();
        CountDownLatch ran = new CountDownLatch(1);
        assertThatThrownBy(() -> executor.execute(ran::countDown)).isInstanceOf(RejectedExecutionException.class);
        assertThat(ran.getCount()).isEqualTo(1);
    }

    @Test
    void boundedAwaitDoesNotHangForever() throws Exception {
        ThreadPoolTaskExecutor executor = executor();
        CountDownLatch block = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                block.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        long t0 = System.currentTimeMillis();
        executor.shutdown();
        long elapsed = System.currentTimeMillis() - t0;
        assertThat(elapsed).isLessThan(5000);
        block.countDown();
    }

    private ThreadPoolTaskExecutor executor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(2);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(1);
        executor.initialize();
        return executor;
    }
}
