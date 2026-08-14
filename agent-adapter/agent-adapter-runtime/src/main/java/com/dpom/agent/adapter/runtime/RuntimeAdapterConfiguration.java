package com.dpom.agent.adapter.runtime;

import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.dpom.agent.common.runtime.RuntimeEvidenceClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 运行时证据适配器自动装配。
 */
@Configuration
public class RuntimeAdapterConfiguration {

    /**
     * 构造运行时证据客户端（DPOMBaseMCPServer，REST）。
     *
     * @param baseUrl          服务地址
     * @param connectTimeoutMs 连接超时毫秒
     * @param readTimeoutMs    读取超时毫秒
     * @return 运行时证据客户端
     */
    @Bean
    public RuntimeEvidenceClient runtimeEvidenceClient(
            @Value("${dpom.runtime.base-url:http://localhost:8082}") String baseUrl,
            @Value("${dpom.runtime.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${dpom.runtime.read-timeout-ms:10000}") int readTimeoutMs) {
        return new DpomBaseMcpClient(buildRestClient(baseUrl, connectTimeoutMs, readTimeoutMs));
    }

    /**
     * 构造日志模板挖掘客户端（Drain3 MCP Server）。
     *
     * @param baseUrl Drain3 MCP 服务地址（如 http://localhost:8100）
     * @return 日志模板挖掘客户端
     */
    @Bean
    public LogTemplateMinerClient logTemplateMinerClient(
            @Value("${dpom.logtemplate.mcp-base-url:http://localhost:8100}") String baseUrl) {
        return new McpLogTemplateMinerClient(() -> {
            HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(baseUrl).build();
            return McpClient.sync(transport).build();
        });
    }

    /**
     * 构造带超时的 RestClient。
     */
    private RestClient buildRestClient(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
