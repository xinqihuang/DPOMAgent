package com.dpom.agent.web;

import com.dpom.agent.common.diagnosisevent.DeliveryOutcome;
import com.dpom.agent.common.diagnosisevent.DiagnosisEventDeliveryRequest;
import com.dpom.agent.web.diagnosisevent.DiagnosisEventHttpDeliveryAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * RestClient Diagnosis Event HMAC 投递契约测试。
 */
class DiagnosisEventHttpDeliveryAdapterTest {

    private static final URI DESTINATION = URI.create("https://evaluation.example/internal/diagnosis-events");
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes();
    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private DiagnosisEventHttpDeliveryAdapter adapter;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T06:30:00Z"), ZoneOffset.UTC);
        adapter = new DiagnosisEventHttpDeliveryAdapter(builder.build(), new ObjectMapper(), DESTINATION,
                SECRET, clock, 128);
    }

    @Test
    void sendsCanonicalBodyIdentityHashAndDeterministicSignature() {
        server.expect(once(), requestTo(DESTINATION)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("X-DPOM-Event-Id", "event-1"))
                .andExpect(header("Idempotency-Key", "idem-1"))
                .andExpect(header("X-DPOM-Content-SHA256", "0".repeat(64)))
                .andExpect(header("X-DPOM-Timestamp", "1787293800"))
                .andExpect(header("X-DPOM-Signature",
                        "sha256=e83309cc2aa815167e951d83bece1b1d8e51b87032ae78a187d832ea05dd5935"))
                .andExpect(content().json("{\"result\":\"ok\"}", true))
                .andRespond(withSuccess("{\"outcome\":\"ACCEPTED\"}", MediaType.APPLICATION_JSON));

        var result = adapter.deliver(request("{\"result\":\"ok\"}"));

        assertThat(result.outcome()).isEqualTo(DeliveryOutcome.ACCEPTED);
        server.verify();
    }

    @Test
    void mapsRetryableAndPermanentHttpStatusesWithoutResponseDetails() {
        server.expect(requestTo(DESTINATION)).andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
        assertThat(adapter.deliver(request("{}"))).extracting("outcome", "errorCode")
                .containsExactly(DeliveryOutcome.RETRYABLE_FAILURE, "HTTP_429");
        server.reset();
        server.expect(requestTo(DESTINATION)).andRespond(withResourceNotFound());
        assertThat(adapter.deliver(request("{}"))).extracting("outcome", "errorCode")
                .containsExactly(DeliveryOutcome.PERMANENT_REJECTION, "HTTP_404");
    }

    @Test
    void mapsConflictAndMalformedOrOversizedAcknowledgementsFailClosed() {
        server.expect(requestTo(DESTINATION)).andRespond(withStatus(org.springframework.http.HttpStatus.CONFLICT));
        assertThat(adapter.deliver(request("{}"))).extracting("outcome", "errorCode")
                .containsExactly(DeliveryOutcome.IDEMPOTENCY_CONFLICT, "IDEMPOTENCY_CONFLICT");
        server.reset();
        server.expect(requestTo(DESTINATION)).andRespond(withSuccess("not-json", MediaType.TEXT_PLAIN));
        assertThat(adapter.deliver(request("{}"))).extracting("outcome", "errorCode")
                .containsExactly(DeliveryOutcome.RETRYABLE_FAILURE, "MALFORMED_ACKNOWLEDGEMENT");
        server.reset();
        server.expect(requestTo(DESTINATION)).andRespond(withSuccess("x".repeat(129), MediaType.APPLICATION_JSON));
        assertThat(adapter.deliver(request("{}"))).extracting("outcome", "errorCode")
                .containsExactly(DeliveryOutcome.RETRYABLE_FAILURE, "ACKNOWLEDGEMENT_TOO_LARGE");
    }

    @Test
    void rejectsUnsafeDestinationAndWeakBoundsWithoutLeakingSecret() {
        assertThatThrownBy(() -> new DiagnosisEventHttpDeliveryAdapter(builder.build(), new ObjectMapper(),
                URI.create("http://evaluation.example/events?secret=value"), SECRET, Clock.systemUTC(), 128))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_HTTPS_DESTINATION")
                .hasMessageNotContaining("value");
        assertThatThrownBy(() -> new DiagnosisEventHttpDeliveryAdapter(builder.build(), new ObjectMapper(),
                DESTINATION, SECRET, Clock.systemUTC(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_ACKNOWLEDGEMENT_LIMIT");
    }

    private DiagnosisEventDeliveryRequest request(String body) {
        return new DiagnosisEventDeliveryRequest("event-1", "idem-1", body, "0".repeat(64));
    }
}
