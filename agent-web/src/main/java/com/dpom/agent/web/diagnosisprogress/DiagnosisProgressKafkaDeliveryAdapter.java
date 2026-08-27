package com.dpom.agent.web.diagnosisprogress;

import com.dpom.agent.common.diagnosisevent.DeliveryAcknowledgement;
import com.dpom.agent.common.diagnosisevent.DeliveryOutcome;
import com.dpom.agent.common.diagnosisprogress.DiagnosisProgressDeliveryPort;
import com.dpom.agent.common.diagnosisprogress.DiagnosisProgressDeliveryRequest;
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

/** 向固定 progress topic 发送有界、冻结的 Diagnosis Progress v1 记录。 */
public final class DiagnosisProgressKafkaDeliveryAdapter implements DiagnosisProgressDeliveryPort, AutoCloseable {

    private static final int MAX_VALUE_BYTES = 8 * 1024;
    private static final int MAX_HEADER_AND_KEY_BYTES = 2 * 1024;

    private final Producer<String, byte[]> producer;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final Duration acknowledgementTimeout;

    /** 创建等待 broker acknowledgement 的有界适配器。 */
    public DiagnosisProgressKafkaDeliveryAdapter(Producer<String, byte[]> producer, ObjectMapper objectMapper,
            String topic, Duration acknowledgementTimeout) {
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.topic = required(topic, "INVALID_PROGRESS_TOPIC");
        if (!"dpom.diagnosis-progress.v1".equals(topic)) {
            throw new IllegalArgumentException("INVALID_PROGRESS_TOPIC");
        }
        if (acknowledgementTimeout == null || acknowledgementTimeout.isZero()
                || acknowledgementTimeout.isNegative()) {
            throw new IllegalArgumentException("INVALID_PROGRESS_ACK_TIMEOUT");
        }
        this.acknowledgementTimeout = acknowledgementTimeout;
    }

    @Override
    public DeliveryAcknowledgement deliver(DiagnosisProgressDeliveryRequest request) {
        try {
            byte[] body = request.canonicalJson().getBytes(StandardCharsets.UTF_8);
            JsonNode progress = objectMapper.readTree(body);
            String progressId = required(progress.path("progressId").asText(), "INVALID_PROGRESS_ID");
            String investigationId = required(progress.path("investigationId").asText(),
                    "INVALID_INVESTIGATION_ID");
            String schemaVersion = required(progress.path("schemaVersion").asText(),
                    "INVALID_PROGRESS_SCHEMA");
            String authorityEpoch = required(progress.at("/sourceAuthority/authorityEpoch").asText(),
                    "INVALID_AUTHORITY_EPOCH");
            String authorityService = progress.at("/sourceAuthority/service").asText();
            if (!progressId.equals(request.progressId()) || !investigationId.equals(request.investigationId())
                    || !schemaVersion.matches("1\\.[0-9]+") || !"DPOMAgent".equals(authorityService)) {
                return acknowledgement(DeliveryOutcome.PERMANENT_REJECTION,
                        "PROGRESS_VERIFICATION_FAILED");
            }
            List<Header> headers = List.of(header("dpom-contract", "diagnosis-progress"),
                    header("dpom-schema-version", schemaVersion), header("dpom-progress-id", progressId),
                    header("dpom-authority-epoch", authorityEpoch),
                    header("dpom-canonical-sha256", request.canonicalSha256()),
                    header("content-type", "application/json"));
            validateBounds(investigationId, body, headers);
            producer.send(new ProducerRecord<>(topic, null, investigationId, body, headers))
                    .get(acknowledgementTimeout.toMillis(), TimeUnit.MILLISECONDS);
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

    private void validateBounds(String key, byte[] body, List<Header> headers) {
        int keyBytes = key.getBytes(StandardCharsets.UTF_8).length;
        int headerBytes = headers.stream().mapToInt(header ->
                header.key().getBytes(StandardCharsets.UTF_8).length + header.value().length).sum();
        if (body.length > MAX_VALUE_BYTES || keyBytes > 128
                || keyBytes + headerBytes > MAX_HEADER_AND_KEY_BYTES) {
            throw new IllegalArgumentException("PROGRESS_RECORD_TOO_LARGE");
        }
    }

    private static RecordHeader header(String name, String value) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > 128) {
            throw new IllegalArgumentException("PROGRESS_HEADER_INVALID");
        }
        return new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static String required(String value, String code) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(code);
        }
        return value;
    }

    private static String stable(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{0,63}")
                ? value : "INVALID_KAFKA_PROGRESS";
    }

    private static DeliveryAcknowledgement acknowledgement(DeliveryOutcome outcome, String code) {
        return new DeliveryAcknowledgement(outcome, code);
    }
}
