package com.dpom.agent.web.config;

import com.dpom.agent.web.filter.RequestSizeLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 调查提交请求体上限过滤器注册：仅作用于 POST /api/v1/investigations。
 */
@Configuration
public class RequestSizeLimitConfig {

    @Bean
    public FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilterRegistration(
            @Value("${dpom.api.max-body-bytes:1153434}") long maxBodyBytes, ObjectMapper objectMapper) {
        FilterRegistrationBean<RequestSizeLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestSizeLimitFilter(maxBodyBytes, objectMapper));
        registration.addUrlPatterns("/api/v1/investigations");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.setName("requestSizeLimitFilter");
        return registration;
    }
}
