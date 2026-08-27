package com.dpom.agent.web.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 始终绑定配置，并仅对已启用能力执行 fail-closed 校验。
 */
@Configuration
@EnableConfigurationProperties(DiagnosisEventProperties.class)
public class DiagnosisEventPropertiesConfiguration {

    /** 创建启动期配置校验器。 */
    @Bean
    InitializingBean diagnosisEventPropertiesValidator(DiagnosisEventProperties properties) {
        return () -> {
            properties.validateDelivery();
            properties.validateReplay();
        };
    }
}
