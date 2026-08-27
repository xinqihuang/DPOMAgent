package com.dpom.agent.web;

import com.dpom.agent.common.diagnosisevent.DeliveryOutcome;
import com.dpom.agent.common.diagnosisprogress.DiagnosisProgressDeliveryRequest;
import com.dpom.agent.web.diagnosisprogress.DiagnosisProgressKafkaDeliveryAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Progress Kafka 适配器的固定 topic、key、headers、边界与确认测试。 */
class DiagnosisProgressKafkaDeliveryAdapterTest {

    private static final String CONTENT = """
            {"progressId":"944e0e9f-11c2-39e2-9ac4-dd4135841c6d","schemaVersion":"1.1",
            "occurredAt":"2026-08-27T01:00:00Z","investigationId":"investigation-1",
            "progressSequence":1,"aggregateVersion":0,
            "sourceAuthority":{"service":"DPOMAgent","authorityEpoch":"phase1b-test"},
            "status":"ACCEPTED","stage":"ADMISSION","summaryCode":"INVESTIGATION_CREATED"}
            """;

    @Test
    void publishesExactBytesWithInvestigationKeyAndRequiredHeaders() {
        var producer = new MockProducer<String, byte[]>(true, new StringSerializer(), new ByteArraySerializer());
        var adapter = new DiagnosisProgressKafkaDeliveryAdapter(producer, new ObjectMapper(),
                "dpom.diagnosis-progress.v1", Duration.ofSeconds(1));
        var request = new DiagnosisProgressDeliveryRequest("944e0e9f-11c2-39e2-9ac4-dd4135841c6d",
                "investigation-1", CONTENT, "a".repeat(64));

        assertThat(adapter.deliver(request).outcome()).isEqualTo(DeliveryOutcome.ACCEPTED);
        var record = producer.history().getFirst();
        assertThat(record.topic()).isEqualTo("dpom.diagnosis-progress.v1");
        assertThat(record.key()).isEqualTo("investigation-1");
        assertThat(new String(record.value(), StandardCharsets.UTF_8)).isEqualTo(CONTENT);
        assertThat(header(record.headers().lastHeader("dpom-contract"))).isEqualTo("diagnosis-progress");
        assertThat(header(record.headers().lastHeader("dpom-schema-version"))).isEqualTo("1.1");
        assertThat(header(record.headers().lastHeader("dpom-authority-epoch"))).isEqualTo("phase1b-test");
        assertThat(header(record.headers().lastHeader("dpom-canonical-sha256"))).isEqualTo("a".repeat(64));
    }

    @Test
    void rejectsIdentityMismatchWithoutSending() {
        var producer = new MockProducer<String, byte[]>(true, new StringSerializer(), new ByteArraySerializer());
        var adapter = new DiagnosisProgressKafkaDeliveryAdapter(producer, new ObjectMapper(),
                "dpom.diagnosis-progress.v1", Duration.ofSeconds(1));
        var request = new DiagnosisProgressDeliveryRequest("00000000-0000-0000-0000-000000000000",
                "investigation-1", CONTENT, "a".repeat(64));

        assertThat(adapter.deliver(request)).extracting("outcome", "errorCode")
                .containsExactly(DeliveryOutcome.PERMANENT_REJECTION, "PROGRESS_VERIFICATION_FAILED");
        assertThat(producer.history()).isEmpty();
    }

    private static String header(Header value) {
        return new String(value.value(), StandardCharsets.UTF_8);
    }
}
