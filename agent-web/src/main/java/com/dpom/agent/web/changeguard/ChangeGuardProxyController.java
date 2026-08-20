package com.dpom.agent.web.changeguard;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * 固定目标、固定路径的 Change Guard 透明转发入口。
 */
@RestController
@RequestMapping("/change-guard-api")
public class ChangeGuardProxyController {

    private static final String PROXY_PREFIX = "/change-guard-api";
    private final RestClient client;

    public ChangeGuardProxyController(RestClient.Builder builder,
            @Value("${dpom.change-guard.base-url:http://localhost:8081}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @RequestMapping(value = {"/api/v1/operations", "/api/v1/operations/**"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<byte[]> forward(HttpServletRequest request,
            @RequestBody(required = false) byte[] body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String path = request.getRequestURI().substring(PROXY_PREFIX.length());
        RestClient.RequestBodySpec outgoing = client.method(HttpMethod.valueOf(request.getMethod()))
                .uri(path).accept(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) outgoing.header("Idempotency-Key", idempotencyKey);
        RestClient.RequestHeadersSpec<?> requestSpec = body == null ? outgoing
                : outgoing.contentType(MediaType.APPLICATION_JSON).body(body);
        return requestSpec.exchange((sent, response) -> copyResponse(response));
    }

    private ResponseEntity<byte[]> copyResponse(org.springframework.http.client.ClientHttpResponse response)
            throws IOException {
        HttpHeaders headers = new HttpHeaders();
        if (response.getHeaders().getContentType() != null) {
            headers.setContentType(response.getHeaders().getContentType());
        }
        return new ResponseEntity<>(response.getBody().readAllBytes(), headers, response.getStatusCode());
    }
}
