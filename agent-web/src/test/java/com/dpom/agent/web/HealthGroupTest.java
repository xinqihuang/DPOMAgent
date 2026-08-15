package com.dpom.agent.web;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.dpom.agent.common.runtime.RuntimeEvidenceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * RANDOM_PORT 真实 HTTP：liveness 200、readiness 200/503、health 不主动调用外部适配器。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthGroupTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired @Qualifier("investigationExecutor") private ThreadPoolTaskExecutor executor;

    @MockitoBean private ModelClient modelClient;
    @MockitoBean private CodeGraphClient codeGraphClient;
    @MockitoBean private LogTemplateMinerClient logTemplateMinerClient;
    @MockitoBean private RuntimeEvidenceClient runtimeEvidenceClient;

    @Test
    void livenessIsUp() {
        ResponseEntity<String> resp = restTemplate.getForEntity("/actuator/health/liveness", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void readinessUpWhenIdle() {
        ResponseEntity<String> resp = restTemplate.getForEntity("/actuator/health/readiness", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void readinessDownWhenExecutorSaturated() throws Exception {
        CountDownLatch block = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(14);
        for (int i = 0; i < 14; i++) {
            executor.execute(() -> {
                try {
                    block.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        awaitActive(4);
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity("/actuator/health/readiness", String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(resp.getBody()).contains("\"status\":\"DOWN\"");
        } finally {
            block.countDown();
            done.await(30, TimeUnit.SECONDS);
        }
    }

    @Test
    void healthDoesNotActivelyCallAdapters() {
        restTemplate.getForEntity("/actuator/health", String.class);
        restTemplate.getForEntity("/actuator/health/readiness", String.class);
        verifyNoInteractions(modelClient, codeGraphClient, logTemplateMinerClient, runtimeEvidenceClient);
    }

    private void awaitActive(int expected) throws Exception {
        for (int i = 0; i < 50 && executor.getActiveCount() < expected; i++) {
            Thread.sleep(20);
        }
        assertThat(executor.getActiveCount()).isGreaterThanOrEqualTo(expected);
    }
}
