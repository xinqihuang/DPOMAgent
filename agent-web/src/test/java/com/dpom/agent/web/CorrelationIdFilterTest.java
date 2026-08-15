package com.dpom.agent.web;

import com.dpom.agent.web.filter.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * correlationId 过滤器：缺省生成、合法采用、非法 400 + 新 id、MDC finally 清理、全响应回显。
 */
class CorrelationIdFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generatesAndEchoesWhenAbsentAndClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdc = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdc.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        new CorrelationIdFilter(objectMapper).doFilter(request, response, chain);

        String id = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(id).matches("[A-Za-z0-9-]{36}");
        assertThat(mdc.get()).isEqualTo(id);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void acceptsAndEchoesValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/investigations");
        request.addHeader(CorrelationIdFilter.HEADER, "abc-123_456.789");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        new CorrelationIdFilter(objectMapper).doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-123_456.789");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void rejectsInvalidWith400AndFreshCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(CorrelationIdFilter.HEADER, "bad header with spaces!");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new CorrelationIdFilter(objectMapper).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        String id = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(id).matches("[A-Za-z0-9-]{36}");
        assertThat(response.getContentAsString()).contains("BAD_REQUEST");
        verify(chain, never()).doFilter(any(), any());
    }
}
