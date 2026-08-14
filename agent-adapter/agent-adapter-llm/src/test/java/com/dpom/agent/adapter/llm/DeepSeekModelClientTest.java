package com.dpom.agent.adapter.llm;

import com.dpom.agent.common.llm.ChatMessage;
import com.dpom.agent.common.llm.ModelProviderException;
import com.dpom.agent.common.llm.ModelTimeoutException;
import com.dpom.agent.common.llm.ModelTurnRequest;
import com.dpom.agent.common.llm.ModelTurnResult;
import com.dpom.agent.common.llm.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * DeepSeek 模型客户端验收测试：文本回答、工具调用、错误与超时映射。
 */
class DeepSeekModelClientTest {

    private MockRestServiceServer server;

    private DeepSeekModelClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DeepSeekModelClient(builder.build(), "deepseek-v4-pro");
    }

    /**
     * 文本回答。
     */
    @Test
    void returnsTextAnswer() {
        server.expect(requestTo("/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"设备创建成功但未落库\"}}],"
                                + "\"model\":\"deepseek-v4-pro\",\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20}}",
                        MediaType.APPLICATION_JSON));

        ModelTurnResult result = client.complete(new ModelTurnRequest(
                List.of(ChatMessage.user("症状是什么")), List.of()));

        assertThat(result.message().content()).isEqualTo("设备创建成功但未落库");
        assertThat(result.model()).isEqualTo("deepseek-v4-pro");
        assertThat(result.completionTokens()).isEqualTo(20);
        server.verify();
    }

    /**
     * 工具调用。
     */
    @Test
    void returnsToolCall() {
        server.expect(requestTo("/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":"
                                + "[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"search_logs\","
                                + "\"arguments\":\"{\\\"keyword\\\":\\\"INSERT\\\"}\"}}]}}],\"model\":\"deepseek-v4-pro\"}",
                        MediaType.APPLICATION_JSON));

        ModelTurnResult result = client.complete(new ModelTurnRequest(
                List.of(ChatMessage.user("查日志")),
                List.of(new ToolDefinition("search_logs", "搜索日志", "{\"type\":\"object\"}"))));

        assertThat(result.message().toolCalls()).hasSize(1);
        assertThat(result.message().toolCalls().get(0).name()).isEqualTo("search_logs");
        assertThat(result.message().toolCalls().get(0).argumentsJson()).contains("INSERT");
        server.verify();
    }

    /**
     * 错误映射。
     */
    @Test
    void mapsProviderError() {
        server.expect(requestTo("/chat/completions"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.complete(
                new ModelTurnRequest(List.of(ChatMessage.user("x")), List.of())))
                .isInstanceOf(ModelProviderException.class);
        server.verify();
    }

    /**
     * 连接失败（ResourceAccessException）映射为超时异常。
     */
    @Test
    void mapsTimeout() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(500);
        factory.setReadTimeout(100);
        RestClient restClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:1")
                .requestFactory(factory)
                .build();
        DeepSeekModelClient timeoutClient = new DeepSeekModelClient(restClient, "deepseek-v4-pro");

        assertThatThrownBy(() -> timeoutClient.complete(
                new ModelTurnRequest(List.of(ChatMessage.user("x")), List.of())))
                .isInstanceOf(ModelTimeoutException.class);
    }
}
