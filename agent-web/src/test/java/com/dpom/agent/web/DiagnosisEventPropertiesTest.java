package com.dpom.agent.web;

import com.dpom.agent.web.config.DiagnosisEventProperties;
import com.dpom.agent.web.config.DiagnosisEventPropertiesConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Diagnosis Event 条件配置启动测试。
 */
class DiagnosisEventPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DiagnosisEventPropertiesConfiguration.class);

    @Test
    void deliveryAndReplayAreDisabledByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            DiagnosisEventProperties properties = context.getBean(DiagnosisEventProperties.class);
            assertThat(properties.getDelivery().isEnabled()).isFalse();
            assertThat(properties.getDelivery().getKafka().isProgressEnabled()).isFalse();
            assertThat(properties.getReplay().isEnabled()).isFalse();
        });
    }

    @Test
    void completeEnabledConfigurationStarts() {
        runner.withPropertyValues(
                "dpom.evaluation.delivery.enabled=true",
                "dpom.evaluation.delivery.destination=https://evaluation.example/events",
                "dpom.evaluation.delivery.hmac-secret=0123456789abcdef0123456789abcdef",
                "dpom.evaluation.replay.enabled=true",
                "dpom.evaluation.replay.hmac-secret=abcdef0123456789abcdef0123456789")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void enabledDeliveryFailsClosedForIncompleteUnsafeOrNonPositiveValues() {
        assertStartupFailure("dpom.evaluation.delivery.enabled=true", "INVALID_HTTPS_DESTINATION");
        assertStartupFailure("dpom.evaluation.delivery.enabled=true",
                "dpom.evaluation.delivery.destination=http://evaluation.example/events",
                "dpom.evaluation.delivery.hmac-secret=0123456789abcdef0123456789abcdef",
                "INVALID_HTTPS_DESTINATION");
        assertStartupFailure("dpom.evaluation.delivery.enabled=true",
                "dpom.evaluation.delivery.destination=https://evaluation.example/events",
                "dpom.evaluation.delivery.hmac-secret=short", "WEAK_DELIVERY_HMAC_SECRET");
        assertStartupFailure("dpom.evaluation.delivery.enabled=true",
                "dpom.evaluation.delivery.destination=https://evaluation.example/events",
                "dpom.evaluation.delivery.hmac-secret=0123456789abcdef0123456789abcdef",
                "dpom.evaluation.delivery.batch-size=0", "INVALID_DELIVERY_BOUNDS");
    }

    @Test
    void enabledReplayRequiresStrongSecretAndPositiveBounds() {
        assertStartupFailure("dpom.evaluation.replay.enabled=true", "WEAK_REPLAY_HMAC_SECRET");
        assertStartupFailure("dpom.evaluation.replay.enabled=true",
                "dpom.evaluation.replay.hmac-secret=abcdef0123456789abcdef0123456789",
                "dpom.evaluation.replay.nonce-ttl=0s", "INVALID_REPLAY_BOUNDS");
    }

    @Test
    void progressAdmissionRequiresKafkaModeAndFixedTopic() {
        assertStartupFailure("dpom.evaluation.delivery.enabled=true",
                "dpom.evaluation.delivery.destination=https://evaluation.example/events",
                "dpom.evaluation.delivery.hmac-secret=0123456789abcdef0123456789abcdef",
                "dpom.evaluation.delivery.kafka.progress-enabled=true", "PROGRESS_REQUIRES_KAFKA_MODE");
        assertStartupFailure("dpom.evaluation.delivery.enabled=true",
                "dpom.evaluation.delivery.mode=KAFKA",
                "dpom.evaluation.delivery.kafka.bootstrap-servers=localhost:9092",
                "dpom.evaluation.delivery.kafka.producer-identity=dpom-agent-test",
                "dpom.evaluation.delivery.kafka.progress-enabled=true",
                "dpom.evaluation.delivery.kafka.progress-topic=wrong-topic", "INVALID_KAFKA_PROGRESS_CONFIG");
    }

    private void assertStartupFailure(String... valuesAndExpectedMessage) {
        String expected = valuesAndExpectedMessage[valuesAndExpectedMessage.length - 1];
        String[] values = java.util.Arrays.copyOf(valuesAndExpectedMessage, valuesAndExpectedMessage.length - 1);
        runner.withPropertyValues(values).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(expected);
        });
    }
}
