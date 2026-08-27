package com.dpom.agent.web.diagnosisevent;

import com.dpom.agent.common.diagnosisevent.DeliveryAcknowledgement;
import com.dpom.agent.common.diagnosisevent.DeliveryOutcome;
import com.dpom.agent.common.diagnosisevent.DiagnosisEventDeliveryPort;
import com.dpom.agent.common.diagnosisevent.DiagnosisEventDeliveryRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 将同一份冻结 Outbox 正文投递到固定的 Diagnosis Event v2 topic。 */
public final class DiagnosisEventKafkaDeliveryAdapter implements DiagnosisEventDeliveryPort, AutoCloseable {

    private final Producer<String, byte[]> producer;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final String producerIdentity;
    private final Duration acknowledgementTimeout;

    /** 创建有界且等待 broker acknowledgement 的 Kafka 适配器。 */
    public DiagnosisEventKafkaDeliveryAdapter(Producer<String, byte[]> producer, ObjectMapper objectMapper,
            String topic, String producerIdentity, Duration acknowledgementTimeout) {
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.topic = require(topic, 128, "INVALID_KAFKA_TOPIC");
        if (!"dpom.diagnosis-event.v2".equals(topic)) {
            throw new IllegalArgumentException("INVALID_KAFKA_TOPIC");
        }
        this.producerIdentity = require(producerIdentity, 128, "INVALID_KAFKA_PRODUCER_IDENTITY");
        if (acknowledgementTimeout == null || acknowledgementTimeout.isZero()
                || acknowledgementTimeout.isNegative()) {
            throw new IllegalArgumentException("INVALID_KAFKA_ACK_TIMEOUT");
        }
        this.acknowledgementTimeout = acknowledgementTimeout;
    }

    @Override
    public DeliveryAcknowledgement deliver(DiagnosisEventDeliveryRequest request) {
        try {
            JsonNode event = objectMapper.readTree(request.canonicalJson());
            String schemaVersion = event.path("schemaVersion").asText();
            String investigationId = require(event.path("investigationId").asText(), 128,
                    "INVALID_INVESTIGATION_ID");
            String intentId = require(event.at("/sourceAuthority/publicationIntentId").asText(), 128,
                    "INVALID_PUBLICATION_INTENT_ID");
            String eventProducer = event.at("/producer/instanceId").asText();
            if (!schemaVersion.matches("2\\.[0-9]+") || !producerIdentity.equals(eventProducer)) {
                return acknowledgement(DeliveryOutcome.PERMANENT_REJECTION, "PRODUCER_VERIFICATION_FAILED");
            }
            List<Header> headers = List.of(header("content-type", "application/json"),
                    header("schema-version", schemaVersion), header("producer", producerIdentity),
                    header("canonical-sha256", request.canonicalSha256()),
                    header("publication-intent-id", intentId));
            var record = new ProducerRecord<String, byte[]>(topic, null, investigationId,
                    request.canonicalJson().getBytes(StandardCharsets.UTF_8), headers);
            producer.send(record).get(acknowledgementTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return acknowledgement(DeliveryOutcome.ACCEPTED, null);
        } catch (IllegalArgumentException exception) {
            return acknowledgement(DeliveryOutcome.PERMANENT_REJECTION, stable(exception.getMessage()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return acknowledgement(DeliveryOutcome.RETRYABLE_FAILURE, "KAFKA_INTERRUPTED");
        } catch (Exception exception) {
            return acknowledgement(DeliveryOutcome.RETRYABLE_FAILURE, "KAFKA_DELIVERY_FAILED");
        }
    }

    @Override
    public void close() {
        producer.close(Duration.ofSeconds(5));
    }

    private static RecordHeader header(String name, String value) {
        return new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static String require(String value, int maximum, String code) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(code);
        }
        return value;
    }

    private static String stable(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{0,63}") ? value : "INVALID_KAFKA_EVENT";
    }

    private static DeliveryAcknowledgement acknowledgement(DeliveryOutcome outcome, String errorCode) {
        return new DeliveryAcknowledgement(outcome, errorCode);
    }
}
