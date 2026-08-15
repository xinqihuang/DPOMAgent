package com.dpom.agent.web;

import com.dpom.agent.web.filter.RequestSizeLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 请求体上限过滤器单元测试：Content-Length 超限、无 Content-Length/chunked 流式超限、放行、非目标路径跳过。
 */
class RequestSizeLimitFilterTest {

    private static final long MAX = 1024;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsWhenContentLengthExceedsLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/investigations");
        request.setContent(new byte[2048]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new RequestSizeLimitFilter(MAX, objectMapper).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsStreamedBodyWithoutContentLength() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/investigations");
        when(request.getContextPath()).thenReturn("");
        when(request.getContentLengthLong()).thenReturn(-1L);
        when(request.getInputStream()).thenReturn(stream(new byte[2048]));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new RequestSizeLimitFilter(MAX, objectMapper).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void passesThroughWhenBodyWithinLimit() throws Exception {
        byte[] body = "{\"serviceCode\":\"svc\"}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/investigations");
        request.setContent(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new RequestSizeLimitFilter(MAX, objectMapper).doFilter(request, response, chain);

        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(chain).doFilter(captor.capture(), eq(response));
        assertThat(captor.getValue().getContentLengthLong()).isEqualTo(body.length);
    }

    @Test
    void skipsNonInvestigationPost() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new RequestSizeLimitFilter(MAX, objectMapper).doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
    }

    private ServletInputStream stream(byte[] bytes) {
        return new DelegatingServletInputStream(new ByteArrayInputStream(bytes));
    }
}
