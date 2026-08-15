package com.dpom.agent.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 HTTP 集成：过滤器已在 Servlet 容器注册，超限返回 413 + PAYLOAD_TOO_LARGE，GET 不受影响。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestSizeLimitHttpTest {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void postOverContentLengthLimitReturns413() {
        String big = "x".repeat(1_600_000);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(big, headers);
        ResponseEntity<String> response = restTemplate.exchange("/api/v1/investigations", HttpMethod.POST, entity,
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).contains("PAYLOAD_TOO_LARGE").doesNotContain("x".repeat(100));
    }

    @Test
    void postStreamedBodyOverLimitReturns413() {
        byte[] big = new byte[1_600_000];
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<InputStreamResource> entity = new HttpEntity<>(
                new InputStreamResource(new ByteArrayInputStream(big)), headers);
        ResponseEntity<String> response = restTemplate.exchange("/api/v1/investigations", HttpMethod.POST, entity,
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).contains("PAYLOAD_TOO_LARGE");
    }

    @Test
    void getUnaffectedByBodyLimit() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("status");
    }
}
