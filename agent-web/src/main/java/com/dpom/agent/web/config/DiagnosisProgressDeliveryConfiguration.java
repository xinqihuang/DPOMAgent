package com.dpom.agent.web.config;

import com.dpom.agent.common.diagnosisprogress.DiagnosisProgressDeliveryPort;
import com.dpom.agent.core.diagnosisevent.DiagnosisDeliveryPolicy;
import com.dpom.agent.core.diagnosisprogress.AuthorityProgressDeliveryService;
import com.dpom.agent.core.persistence.authority.AuthorityProgressDao;
import com.dpom.agent.web.diagnosisprogress.AuthorityProgressDeliveryWorker;
import com.dpom.agent.web.diagnosisprogress.AuthorityProgressReadinessHealthIndicator;
import com.dpom.agent.web.diagnosisprogress.DiagnosisProgressKafkaDeliveryAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.HashMap;
import java.util.UUID;

/** 仅在 Kafka 与 Progress admission 都显式启用时装配网络发布能力。 */
@Configuration
@ConditionalOnProperty(name = "dpom.evaluation.delivery.enabled", havingValue = "true")
public class DiagnosisProgressDeliveryConfiguration {

    @Bean
    @ConditionalOnProperty(name = "dpom.evaluation.delivery.kafka.progress-enabled", havingValue = "true")
    DiagnosisProgressDeliveryPort diagnosisProgressDeliveryPort(ObjectMapper objectMapper,
            DiagnosisEventProperties properties) {
        DiagnosisEventProperties.Delivery delivery = properties.getDelivery();
        if (delivery.getMode() != DiagnosisEventProperties.DeliveryMode.KAFKA) {
            throw new IllegalStateException("PROGRESS_REQUIRES_KAFKA_MODE");
        }
        DiagnosisEventProperties.Kafka kafka = delivery.getKafka();
        var settings = new HashMap<String, Object>();
        settings.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        settings.put(ProducerConfig.ACKS_CONFIG, "all");
        settings.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        settings.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        settings.put(ProducerConfig.CLIENT_ID_CONFIG, kafka.getProducerIdentity() + "-progress");
        settings.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        settings.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new DiagnosisProgressKafkaDeliveryAdapter(new KafkaProducer<>(settings), objectMapper,
                kafka.getProgressTopic(), kafka.getAcknowledgementTimeout());
    }

    @Bean
    @ConditionalOnProperty(name = "dpom.evaluation.delivery.kafka.progress-enabled", havingValue = "true")
    AuthorityProgressDeliveryService authorityProgressDeliveryService(AuthorityProgressDao dao,
            DiagnosisProgressDeliveryPort port, DiagnosisDeliveryPolicy policy, Clock clock,
            PlatformTransactionManager transactionManager) {
        return new AuthorityProgressDeliveryService(dao, port, policy, clock, transactionManager);
    }

    @Bean
    @ConditionalOnProperty(name = "dpom.evaluation.delivery.kafka.progress-enabled", havingValue = "true")
    AuthorityProgressDeliveryWorker authorityProgressDeliveryWorker(AuthorityProgressDeliveryService service) {
        return new AuthorityProgressDeliveryWorker(service, "progress-delivery-" + UUID.randomUUID());
    }

    @Bean("authorityProgress")
    @ConditionalOnProperty(name = "dpom.evaluation.delivery.kafka.progress-enabled", havingValue = "true")
    AuthorityProgressReadinessHealthIndicator authorityProgressReadinessHealthIndicator(
            AuthorityProgressDao dao, DiagnosisEventProperties properties) {
        return new AuthorityProgressReadinessHealthIndicator(dao, properties);
    }
}
