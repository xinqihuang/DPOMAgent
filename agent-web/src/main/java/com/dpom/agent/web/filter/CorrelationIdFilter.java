package com.dpom.agent.web.filter;

import com.dpom.agent.web.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * correlationId 过滤器：生成/接受受限格式 X-Correlation-Id，所有响应回显，写入 MDC 并 finally 清理。
 * 非法 header 返回 400 + 新生成的安全 correlationId。
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String ATTR = CorrelationIdFilter.class.getName() + ".correlationId";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final ObjectMapper objectMapper;

    public CorrelationIdFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        if (incoming != null && !incoming.isBlank() && !VALID.matcher(incoming).matches()) {
            String fresh = UUID.randomUUID().toString();
            reject(response, fresh);
            return;
        }
        String id = incoming == null || incoming.isBlank() ? UUID.randomUUID().toString() : incoming;
        response.setHeader(HEADER, id);
        request.setAttribute(ATTR, id);
        MDC.put(MDC_KEY, id);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private void reject(HttpServletResponse response, String correlationId) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setHeader(HEADER, correlationId);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.of("BAD_REQUEST", "invalid X-Correlation-Id"));
    }
}
