package com.dpom.agent.web.config;

import com.dpom.agent.common.diagnosisevent.DiagnosisEventDeliveryPort;
import com.dpom.agent.core.diagnosisevent.DiagnosisDeliveryPolicy;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventDeliveryService;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventLeaseService;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventMetrics;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventStateService;
import com.dpom.agent.core.diagnosisevent.LeaseTokenSource;
import com.dpom.agent.core.persistence.DiagnosisEventOutboxDao;
import com.dpom.agent.web.diagnosisevent.DiagnosisEventDeliveryWorker;
import com.dpom.agent.web.diagnosisevent.DiagnosisEventHttpDeliveryAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
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

    /** 构建带连接与读取超时的 HTTPS/HMAC 端口。 */
    @Bean
    DiagnosisEventDeliveryPort diagnosisEventDeliveryPort(RestClient.Builder builder, ObjectMapper objectMapper,
                                                           DiagnosisEventProperties properties, Clock clock) {
        DiagnosisEventProperties.Delivery value = properties.getDelivery();
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
    DiagnosisEventDeliveryWorker diagnosisEventDeliveryWorker(DiagnosisEventDeliveryService service) {
        return new DiagnosisEventDeliveryWorker(service, "delivery-" + UUID.randomUUID());
    }
}
