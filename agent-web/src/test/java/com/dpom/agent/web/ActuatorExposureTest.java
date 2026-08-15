package com.dpom.agent.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Actuator 暴露面：仅 health/prometheus，/actuator/metrics 404；prometheus 含 dpom 指标且无敏感/高基数标签。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorExposureTest {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void metricsEndpointIsNotExposed() {
        ResponseEntity<String> resp = restTemplate.getForEntity("/actuator/metrics", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void prometheusExposesDpomMetricsWithoutForbiddenLabels() {
        ResponseEntity<String> resp = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("dpom_executor_queue_size").contains("dpom_executor_active");
        assertThat(resp.getBody()).doesNotContain("investigationId", "runId", "tenant", "serviceCode");
    }

    @Test
    void healthEndpointIsExposed() {
        ResponseEntity<String> resp = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
