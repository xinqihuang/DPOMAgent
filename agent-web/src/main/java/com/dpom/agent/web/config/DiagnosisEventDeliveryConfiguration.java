package com.dpom.agent.web.config;

import com.dpom.agent.common.diagnosisevent.DiagnosisEventDeliveryPort;
import com.dpom.agent.core.diagnosisevent.DiagnosisDeliveryPolicy;
import com.dpom.agent.core.diagnosisevent.AuthorityPublicationDeliveryService;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventDeliveryService;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventLeaseService;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventMetrics;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventStateService;
import com.dpom.agent.core.diagnosisevent.LeaseTokenSource;
import com.dpom.agent.core.persistence.DiagnosisEventOutboxDao;
import com.dpom.agent.core.persistence.authority.AuthorityTerminalDao;
import com.dpom.agent.web.diagnosisevent.AuthorityPublicationDeliveryWorker;
import com.dpom.agent.web.diagnosisevent.AuthorityPublicationReadinessHealthIndicator;
import com.dpom.agent.web.diagnosisevent.DiagnosisEventDeliveryWorker;
import com.dpom.agent.web.diagnosisevent.DiagnosisEventHttpDeliveryAdapter;
import com.dpom.agent.web.diagnosisevent.DiagnosisEventKafkaDeliveryAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.UUID;

/**
 * 仅在显式启用时装配外部网络适配器、租约服务和调度工作者。
 */
@Configuration
@ConditionalOnProperty(name = "dpom.evaluation.delivery.enabled", havingValue = "true")
public class DiagnosisEventDeliveryConfiguration {

    /** 构建投递策略。 */
    @Bean
    DiagnosisDeliveryPolicy diagnosisDeliveryPolicy(DiagnosisEventProperties properties) {
        DiagnosisEventProperties.Delivery value = properties.getDelivery();
        return new DiagnosisDeliveryPolicy(value.getMaxAttempts(), value.getMaxEventAge(), value.getBaseDelay(),
                value.getMaxDelay(), value.getLeaseDuration(), value.getBatchSize());
    }

    /** 构建使用 UUID fencing token 的租约服务。 */
    @Bean
    DiagnosisEventLeaseService diagnosisEventLeaseService(DiagnosisEventOutboxDao outboxDao,
                                                           DiagnosisEventStateService stateService,
                                                           DiagnosisDeliveryPolicy policy, Clock clock) {
        LeaseTokenSource tokens = () -> UUID.randomUUID().toString();
        return new DiagnosisEventLeaseService(outboxDao, stateService, policy, clock, tokens);
    }

    /** 按显式模式构建 HTTPS/HMAC 或 Kafka v2 端口。 */
    @Bean
    DiagnosisEventDeliveryPort diagnosisEventDeliveryPort(RestClient.Builder builder, ObjectMapper objectMapper,
                                                           DiagnosisEventProperties properties, Clock clock) {
        DiagnosisEventProperties.Delivery value = properties.getDelivery();
        if (value.getMode() == DiagnosisEventProperties.DeliveryMode.KAFKA) {
            var kafka = value.getKafka();
            var settings = new HashMap<String, Object>();
            settings.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
            settings.put(ProducerConfig.ACKS_CONFIG, "all");
            settings.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
            settings.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
            settings.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            settings.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
            return new DiagnosisEventKafkaDeliveryAdapter(new KafkaProducer<>(settings), objectMapper,
                    kafka.getTopic(), kafka.getProducerIdentity(), kafka.getAcknowledgementTimeout());
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(value.getConnectTimeout());
        factory.setReadTimeout(value.getReadTimeout());
        RestClient client = builder.requestFactory(factory).build();
        return new DiagnosisEventHttpDeliveryAdapter(client, objectMapper, value.getDestination(),
                value.getHmacSecret().getBytes(StandardCharsets.UTF_8), clock,
                value.getMaxAcknowledgementBytes());
    }

    /** 构建投递编排。 */
    @Bean
    DiagnosisEventDeliveryService diagnosisEventDeliveryService(DiagnosisEventLeaseService leaseService,
                                                                 DiagnosisEventDeliveryPort deliveryPort,
                                                                 DiagnosisEventStateService stateService,
                                                                 DiagnosisDeliveryPolicy policy, Clock clock,
                                                                 DiagnosisEventMetrics metrics) {
        return new DiagnosisEventDeliveryService(leaseService, deliveryPort, stateService, policy, clock, metrics);
    }

    /** 构建唯一的有界轮询工作者。 */
    @Bean
    @ConditionalOnProperty(name = "dpom.evaluation.delivery.mode", havingValue = "HTTP", matchIfMissing = true)
    DiagnosisEventDeliveryWorker diagnosisEventDeliveryWorker(DiagnosisEventDeliveryService service) {
        return new DiagnosisEventDeliveryWorker(service, "delivery-" + UUID.randomUUID());
    }

    /** 构建权威终态 Outbox 的传输无关投递编排。 */
    @Bean
    AuthorityPublicationDeliveryService authorityPublicationDeliveryService(AuthorityTerminalDao terminalDao,
            DiagnosisEventDeliveryPort deliveryPort, DiagnosisDeliveryPolicy policy, Clock clock,
            ObjectMapper objectMapper, PlatformTransactionManager transactionManager,
            DiagnosisEventProperties properties) {
        return new AuthorityPublicationDeliveryService(terminalDao, deliveryPort, policy, clock, objectMapper,
                transactionManager, properties.getDelivery().getMode().name());
    }

    /** 构建权威终态 Outbox 的唯一有界工作者。 */
    @Bean
    AuthorityPublicationDeliveryWorker authorityPublicationDeliveryWorker(
            AuthorityPublicationDeliveryService service) {
        return new AuthorityPublicationDeliveryWorker(service, "authority-delivery-" + UUID.randomUUID());
    }

    /** 暴露权威 Outbox 的默认关闭、正文安全容量就绪状态。 */
    @Bean("authorityPublication")
    AuthorityPublicationReadinessHealthIndicator authorityPublicationReadinessHealthIndicator(
            AuthorityTerminalDao terminalDao, DiagnosisEventProperties properties) {
        return new AuthorityPublicationReadinessHealthIndicator(terminalDao, properties);
    }
}
