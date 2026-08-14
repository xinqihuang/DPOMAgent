package com.dpom.agent.adapter.llm;

import com.dpom.agent.common.llm.ModelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * LLM 适配器自动装配：默认使用 DeepSeek（OpenAI 兼容）。
 */
@Configuration
public class LlmAdapterConfiguration {

    /**
     * 构造 DeepSeek 模型客户端（默认 provider）。
     *
     * @param baseUrl          服务地址
     * @param apiKey           API Key
     * @param model            模型名
     * @param connectTimeoutMs 连接超时毫秒
     * @param readTimeoutMs    读取超时毫秒
     * @return 模型客户端
     */
    @Bean
    public ModelClient modelClient(
            @Value("${dpom.llm.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${dpom.llm.api-key:${DEEPSEEK_API_KEY:}}") String apiKey,
            @Value("${dpom.llm.model:${DSH_MODEL:deepseek-v4-pro}}") String model,
            @Value("${dpom.llm.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${dpom.llm.read-timeout-ms:120000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .requestFactory(factory)
                .build();
        return new DeepSeekModelClient(restClient, model);
    }
}
