package com.dpom.agent.web;

import com.dpom.agent.common.diagnosisevent.DeliveryOutcome;
import com.dpom.agent.common.diagnosisevent.DiagnosisEventDeliveryRequest;
import com.dpom.agent.web.diagnosisevent.DiagnosisEventKafkaDeliveryAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Kafka v2 适配器的固定 topic、key、headers 与结果分类测试。 */
class DiagnosisEventKafkaDeliveryAdapterTest {

    private static final String PRODUCER = "dpom-agent-local-01";
    private static final String CONTENT = """
            {"eventId":"00000000-0000-0000-0000-000000000001","eventType":"investigation.completed",
            "schemaVersion":"2.0","producer":{"service":"DPOMAgent","instanceId":"dpom-agent-local-01"},
            "sourceAuthority":{"service":"DPOMAgent","authorityEpoch":"epoch-1","aggregateVersion":1,
            "publicationIntentId":"intent-1"},"investigationId":"investigation-1"}
            """;

    @Test
    void publishesFrozenV2BytesWithInvestigationKeyAndRequiredHeaders() {
        var producer = new MockProducer<String, byte[]>(true, new StringSerializer(), new ByteArraySerializer());
        var adapter = new DiagnosisEventKafkaDeliveryAdapter(producer, new ObjectMapper(),
                "dpom.diagnosis-event.v2", PRODUCER, Duration.ofSeconds(1));
        var request = new DiagnosisEventDeliveryRequest("00000000-0000-0000-0000-000000000001", "idem-1",
                CONTENT, "0".repeat(64));

        assertThat(adapter.deliver(request).outcome()).isEqualTo(DeliveryOutcome.ACCEPTED);
        var record = producer.history().getFirst();
        assertThat(record.topic()).isEqualTo("dpom.diagnosis-event.v2");
        assertThat(record.key()).isEqualTo("investigation-1");
        assertThat(new String(record.value(), StandardCharsets.UTF_8)).isEqualTo(CONTENT);
        assertThat(header(record.headers().lastHeader("schema-version"))).isEqualTo("2.0");
        assertThat(header(record.headers().lastHeader("producer"))).isEqualTo(PRODUCER);
        assertThat(header(record.headers().lastHeader("publication-intent-id"))).isEqualTo("intent-1");
        assertThat(header(record.headers().lastHeader("canonical-sha256"))).isEqualTo("0".repeat(64));
    }

    @Test
    void rejectsLegacyOrMismatchedProducerWithoutSending() {
        var producer = new MockProducer<String, byte[]>(true, new StringSerializer(), new ByteArraySerializer());
        var adapter = new DiagnosisEventKafkaDeliveryAdapter(producer, new ObjectMapper(),
                "dpom.diagnosis-event.v2", "another-producer", Duration.ofSeconds(1));
        var request = new DiagnosisEventDeliveryRequest("event-1", "idem-1", CONTENT, "0".repeat(64));

        assertThat(adapter.deliver(request)).extracting("outcome", "errorCode")
                .containsExactly(DeliveryOutcome.PERMANENT_REJECTION, "PRODUCER_VERIFICATION_FAILED");
        assertThat(producer.history()).isEmpty();
    }

    private static String header(Header value) {
        return new String(value.value(), StandardCharsets.UTF_8);
    }
}
