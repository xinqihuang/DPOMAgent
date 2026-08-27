package com.dpom.agent.web;

import com.dpom.agent.common.diagnosisevent.DeliveryOutcome;
import com.dpom.agent.common.diagnosisevent.DiagnosisEventDeliveryRequest;
import com.dpom.agent.core.diagnosisevent.Rfc8785CanonicalJsonWriter;
import com.dpom.agent.web.diagnosisevent.DiagnosisEventKafkaDeliveryAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/** 显式启用后，验证 DPOMAgent Diagnosis Event v2 到真实本地 broker 的冻结字节合同。 */
@EnabledIfEnvironmentVariable(named = "DPOM_DIAGNOSIS_KAFKA_CONTRACT_BOOTSTRAP", matches = ".+")
class DiagnosisEventLocalKafkaContractTest {

    private static final String TOPIC = "dpom.diagnosis-event.v2";
    private static final String PRODUCER = "dpom-base-prod-cn-north-9-01";

    @Test
    @Timeout(20)
    void realBrokerAcknowledgesAndRetainsExactKeyHeadersAndCanonicalBytes() throws Exception {
        String bootstrap = System.getenv("DPOM_DIAGNOSIS_KAFKA_CONTRACT_BOOTSTRAP");
        ensureTopic(bootstrap);
        String suffix = UUID.randomUUID().toString();
        ObjectMapper mapper = new ObjectMapper();
        Path fixture = Path.of("..", "contracts", "diagnosis-event", "v2", "fixtures", "valid",
                "terminal-inline.json").toAbsolutePath().normalize();
        ObjectNode event = (ObjectNode) mapper.readTree(Files.readAllBytes(fixture));
        event.put("eventId", UUID.randomUUID().toString());
        event.put("investigationId", "INV-LOCAL-KAFKA-" + suffix);
        event.put("idempotencyKey", "INV-LOCAL-KAFKA-" + suffix + ".completed.12");
        ((ObjectNode) event.path("sourceAuthority")).put("publicationIntentId", "PUB-LOCAL-" + suffix);
        byte[] canonical = new Rfc8785CanonicalJsonWriter(mapper).write(event);
        String content = new String(canonical, StandardCharsets.UTF_8);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));

        Map<String, Object> consumerSettings = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ConsumerConfig.GROUP_ID_CONFIG, "dpom-diagnosis-contract-" + suffix,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        try (var consumer = new KafkaConsumer<String, byte[]>(consumerSettings);
             var producer = new KafkaProducer<String, byte[]>(producerSettings(bootstrap));
             var adapter = new DiagnosisEventKafkaDeliveryAdapter(producer, mapper, TOPIC, PRODUCER,
                     Duration.ofSeconds(5))) {
            consumer.subscribe(java.util.List.of(TOPIC));
            consumer.poll(Duration.ofMillis(500));

            var request = new DiagnosisEventDeliveryRequest(event.path("eventId").asText(),
                    event.path("idempotencyKey").asText(), content, digest);
            assertThat(adapter.deliver(request).outcome()).isEqualTo(DeliveryOutcome.ACCEPTED);

            org.apache.kafka.clients.consumer.ConsumerRecord<String, byte[]> matched = null;
            long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
            while (matched == null && System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (event.at("/sourceAuthority/publicationIntentId").asText()
                            .equals(header(record, "publication-intent-id"))) {
                        matched = record;
                        break;
                    }
                }
            }
            assertThat(matched).isNotNull();
            assertThat(matched.key()).isEqualTo(event.path("investigationId").asText());
            assertThat(matched.value()).containsExactly(canonical);
            assertThat(header(matched, "schema-version")).isEqualTo("2.0");
            assertThat(header(matched, "producer")).isEqualTo(PRODUCER);
            assertThat(header(matched, "canonical-sha256")).isEqualTo(digest);
        }
        System.out.println("DPOM_DIAGNOSIS_KAFKA_CONTRACT_STATUS=EXECUTED broker=external");
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
                admin.createTopics(java.util.List.of(new NewTopic(TOPIC, 2, (short) 1))).all().get();
            } catch (ExecutionException exception) {
                if (!(exception.getCause() instanceof TopicExistsException)) { throw exception; }
            }
        }
    }

    private static String header(org.apache.kafka.clients.consumer.ConsumerRecord<String, byte[]> record,
            String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
