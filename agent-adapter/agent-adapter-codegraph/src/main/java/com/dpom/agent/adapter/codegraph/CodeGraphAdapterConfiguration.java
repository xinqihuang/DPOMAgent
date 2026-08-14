package com.dpom.agent.adapter.codegraph;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 代码图适配器自动装配：通过 MCP-over-SSE 接入 CodeGraphContext。
 */
@Configuration
public class CodeGraphAdapterConfiguration {

    /**
     * 构造代码图 MCP 客户端。
     *
     * @param baseUrl CGC 服务地址（如 http://localhost:8080）
     * @return 代码图客户端
     */
    @Bean
    public CodeGraphClient codeGraphClient(
            @Value("${dpom.codegraph.mcp-base-url:http://localhost:8080}") String baseUrl) {
        return new McpCodeGraphClient(() -> {
            HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(baseUrl)
                    .sseEndpoint("/api/v1/mcp/sse")
                    .build();
            return McpClient.sync(transport).build();
        });
    }
}
