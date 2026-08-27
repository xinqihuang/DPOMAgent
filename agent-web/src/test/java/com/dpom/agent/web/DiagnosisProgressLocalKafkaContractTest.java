package com.dpom.agent.web;

import com.dpom.agent.common.diagnosisevent.DeliveryOutcome;
import com.dpom.agent.common.diagnosisprogress.DiagnosisProgressDeliveryRequest;
import com.dpom.agent.web.diagnosisprogress.DiagnosisProgressKafkaDeliveryAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/** 显式启用后，对本地 broker 验证 Progress 的真实 broker acknowledgement 与原字节消费。 */
@EnabledIfEnvironmentVariable(named = "DPOM_PROGRESS_KAFKA_CONTRACT_BOOTSTRAP", matches = ".+")
class DiagnosisProgressLocalKafkaContractTest {

    private static final String TOPIC = "dpom.diagnosis-progress.v1";

    @Test
    @Timeout(20)
    void realBrokerAcknowledgesAndRetainsExactKeyHeadersAndBytes() throws Exception {
        String bootstrap = System.getenv("DPOM_PROGRESS_KAFKA_CONTRACT_BOOTSTRAP");
        ensureTopic(bootstrap);
        String suffix = UUID.randomUUID().toString();
        String progressId = UUID.randomUUID().toString();
        String investigationId = "investigation-" + suffix;
        String content = "{\"progressId\":\"" + progressId + "\",\"schemaVersion\":\"1.1\","
                + "\"occurredAt\":\"2026-08-27T01:00:00Z\",\"investigationId\":\""
                + investigationId + "\",\"progressSequence\":1,\"aggregateVersion\":0,"
                + "\"sourceAuthority\":{\"service\":\"DPOMAgent\",\"authorityEpoch\":\"phase1b-local\"},"
                + "\"status\":\"ACCEPTED\",\"stage\":\"ADMISSION\","
                + "\"summaryCode\":\"INVESTIGATION_CREATED\"}";
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8)));
        Map<String, Object> consumerSettings = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ConsumerConfig.GROUP_ID_CONFIG, "dpom-progress-contract-" + suffix,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        try (var consumer = new KafkaConsumer<String, byte[]>(consumerSettings);
             var producer = new KafkaProducer<String, byte[]>(producerSettings(bootstrap));
             var adapter = new DiagnosisProgressKafkaDeliveryAdapter(producer, new ObjectMapper(), TOPIC,
                     Duration.ofSeconds(5))) {
            consumer.subscribe(java.util.List.of(TOPIC));
            consumer.poll(Duration.ofMillis(500));

            assertThat(adapter.deliver(new DiagnosisProgressDeliveryRequest(progressId, investigationId,
                    content, digest)).outcome()).isEqualTo(DeliveryOutcome.ACCEPTED);

            org.apache.kafka.clients.consumer.ConsumerRecord<String, byte[]> matched = null;
            long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
            while (matched == null && System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (progressId.equals(header(record, "dpom-progress-id"))) {
                        matched = record;
                        break;
                    }
                }
            }
            assertThat(matched).isNotNull();
            assertThat(matched.key()).isEqualTo(investigationId);
            assertThat(new String(matched.value(), StandardCharsets.UTF_8)).isEqualTo(content);
            assertThat(header(matched, "dpom-canonical-sha256")).isEqualTo(digest);
            assertThat(header(matched, "dpom-authority-epoch")).isEqualTo("phase1b-local");
        }
    }

    private static Map<String, Object> producerSettings(String bootstrap) {
        return Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ProducerConfig.ACKS_CONFIG, "all", ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    }

    private static void ensureTopic(String bootstrap) throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", bootstrap))) {
            try {
                admin.createTopics(java.util.List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
            } catch (ExecutionException exception) {
                if (!(exception.getCause() instanceof TopicExistsException)) {
                    throw exception;
                }
            }
        }
    }

    private static String header(org.apache.kafka.clients.consumer.ConsumerRecord<String, byte[]> record,
            String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
