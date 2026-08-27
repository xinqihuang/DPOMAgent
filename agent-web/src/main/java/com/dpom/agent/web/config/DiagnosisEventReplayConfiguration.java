package com.dpom.agent.web.config;

import com.dpom.agent.core.persistence.DiagnosisReplayNonceDao;
import com.dpom.agent.web.diagnosisevent.DiagnosisReplayAuthenticator;
import com.dpom.agent.web.diagnosisevent.DiagnosisReplayRequestValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * 仅在内部重放显式启用时装配认证器、校验器和控制器。
 */
@Configuration
@ConditionalOnProperty(name = "dpom.evaluation.replay.enabled", havingValue = "true")
public class DiagnosisEventReplayConfiguration {

    /** 构建持久化 nonce 的认证器。 */
    @Bean
    DiagnosisReplayAuthenticator diagnosisReplayAuthenticator(DiagnosisReplayNonceDao nonceDao,
                                                               DiagnosisEventProperties properties, Clock clock) {
        DiagnosisEventProperties.Replay value = properties.getReplay();
        return new DiagnosisReplayAuthenticator(nonceDao,
                value.getHmacSecret().getBytes(StandardCharsets.UTF_8),
                value.getTimestampWindow(), value.getNonceTtl(), clock);
    }

    /** 构建严格 allow-list 请求校验器。 */
    @Bean
    DiagnosisReplayRequestValidator diagnosisReplayRequestValidator(ObjectMapper objectMapper,
                                                                     DiagnosisEventProperties properties) {
        DiagnosisEventProperties.Replay value = properties.getReplay();
        return new DiagnosisReplayRequestValidator(objectMapper, value.getMaxOperatorRef(), value.getMaxReason());
    }

}
