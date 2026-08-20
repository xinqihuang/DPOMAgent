package com.dpom.agent.alarm.notification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 通知出站 HTTP 客户端配置：统一构造 {@link RestClient}，不引入第三方 HTTP 客户端。
 */
@Configuration
public class NotificationClientConfig {

    /**
     * 构造告警通知专用 RestClient。
     *
     * @param builder Spring Boot 自动配置的 RestClient.Builder
     * @return RestClient 实例
     */
    @Bean(name = "alarmNotificationRestClient")
    public RestClient alarmNotificationRestClient(RestClient.Builder builder) {
        return builder.build();
    }
}
