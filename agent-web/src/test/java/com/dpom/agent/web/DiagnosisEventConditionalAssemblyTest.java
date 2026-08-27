package com.dpom.agent.web;

import com.dpom.agent.common.diagnosisevent.DiagnosisEventDeliveryPort;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventDeliveryService;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventLeaseService;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventMetrics;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventStateService;
import com.dpom.agent.core.persistence.DiagnosisEventOutboxDao;
import com.dpom.agent.core.persistence.authority.AuthorityTerminalDao;
import com.dpom.agent.web.config.DiagnosisEventDeliveryConfiguration;
import com.dpom.agent.web.config.DiagnosisEventPropertiesConfiguration;
import com.dpom.agent.web.diagnosisevent.DiagnosisEventDeliveryWorker;
import com.dpom.agent.web.diagnosisevent.AuthorityPublicationDeliveryWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Diagnosis Event 网络能力条件装配测试。
 */
class DiagnosisEventConditionalAssemblyTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DiagnosisEventPropertiesConfiguration.class,
                    DiagnosisEventDeliveryConfiguration.class)
            .withBean(DiagnosisEventOutboxDao.class, () -> mock(DiagnosisEventOutboxDao.class))
            .withBean(AuthorityTerminalDao.class, () -> mock(AuthorityTerminalDao.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withBean(DiagnosisEventStateService.class, () -> mock(DiagnosisEventStateService.class))
            .withBean(DiagnosisEventMetrics.class, () -> mock(DiagnosisEventMetrics.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(RestClient.Builder.class, RestClient::builder);

    @Test
    void defaultStartupHasNoDeliveryNetworkOrWorkerBeans() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(DiagnosisEventDeliveryPort.class);
            assertThat(context).doesNotHaveBean(DiagnosisEventLeaseService.class);
            assertThat(context).doesNotHaveBean(DiagnosisEventDeliveryService.class);
            assertThat(context).doesNotHaveBean(DiagnosisEventDeliveryWorker.class);
            assertThat(context).doesNotHaveBean(AuthorityPublicationDeliveryWorker.class);
        });
    }

    @Test
    void explicitCompleteConfigurationAssemblesAllDeliveryBoundaries() {
        runner.withPropertyValues(
                "dpom.evaluation.delivery.enabled=true",
                "dpom.evaluation.delivery.destination=https://evaluation.example/events",
                "dpom.evaluation.delivery.hmac-secret=0123456789abcdef0123456789abcdef")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DiagnosisEventDeliveryPort.class);
                    assertThat(context).hasSingleBean(DiagnosisEventLeaseService.class);
                    assertThat(context).hasSingleBean(DiagnosisEventDeliveryService.class);
                    assertThat(context).hasSingleBean(DiagnosisEventDeliveryWorker.class);
                    assertThat(context).hasSingleBean(AuthorityPublicationDeliveryWorker.class);
                });
    }

    @Test
    void explicitKafkaModeAssemblesTheSameOutboxWorkerWithoutHttpCredentials() {
        runner.withPropertyValues(
                "dpom.evaluation.delivery.enabled=true",
                "dpom.evaluation.delivery.mode=KAFKA",
                "dpom.evaluation.delivery.kafka.bootstrap-servers=localhost:9092",
                "dpom.evaluation.delivery.kafka.producer-identity=dpom-agent-test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DiagnosisEventDeliveryPort.class);
                    assertThat(context).doesNotHaveBean(DiagnosisEventDeliveryWorker.class);
                    assertThat(context).hasSingleBean(AuthorityPublicationDeliveryWorker.class);
                });
    }
}
