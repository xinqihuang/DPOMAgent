package com.dpom.agent.web.config;

import com.dpom.agent.web.filter.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * correlationId 过滤器注册：最高优先级（先于 RequestSizeLimitFilter，保证 413 也带 correlationId）。
 */
@Configuration
public class CorrelationIdConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter(ObjectMapper objectMapper) {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CorrelationIdFilter(objectMapper));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("correlationIdFilter");
        return registration;
    }
}
