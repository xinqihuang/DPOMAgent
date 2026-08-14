package com.dpom.agent.adapter.llm;

import com.dpom.agent.common.llm.ChatMessage;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.llm.ModelException;
import com.dpom.agent.common.llm.ModelProviderException;
import com.dpom.agent.common.llm.ModelTimeoutException;
import com.dpom.agent.common.llm.ModelTurnRequest;
import com.dpom.agent.common.llm.ModelTurnResult;
import com.dpom.agent.common.llm.Role;
import com.dpom.agent.common.llm.ToolDefinition;
import com.dpom.agent.common.llm.ToolInvocation;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/**
 * DeepSeek（OpenAI 兼容 /chat/completions）模型客户端：把内部契约映射到 OpenAI 兼容协议。
 */
public class DeepSeekModelClient implements ModelClient {

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 构造客户端。
     *
     * @param restClient 已配置 baseUrl（如 https://api.deepseek.com）与超时的 RestClient
     * @param model      模型名（如 deepseek-v4-pro）
     */
    public DeepSeekModelClient(RestClient restClient, String model) {
        this.restClient = restClient;
        this.model = model;
    }

    @Override
    public ModelTurnResult complete(ModelTurnRequest request) {
        ChatCompletionRequest body = toRequest(request);
        long start = System.currentTimeMillis();
        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new ModelProviderException("模型调用失败，状态码：" + res.getStatusCode().value());
                    })
                    .body(ChatCompletionResponse.class);
            return toResult(response, System.currentTimeMillis() - start);
        } catch (ResourceAccessException e) {
            throw new ModelTimeoutException("模型调用超时", e);
        } catch (ModelException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ModelProviderException("模型调用失败", e);
        }
    }

    /**
     * 内部请求 → OpenAI 兼容请求。
     */
    private ChatCompletionRequest toRequest(ModelTurnRequest request) {
        List<OpenAiMessage> messages = new ArrayList<>();
        for (ChatMessage message : request.messages()) {
            messages.add(toOpenAiMessage(message));
        }
        List<OpenAiTool> tools = new ArrayList<>();
        if (request.tools() != null) {
            for (ToolDefinition tool : request.tools()) {
                tools.add(toOpenAiTool(tool));
            }
        }
        return new ChatCompletionRequest(model, messages, tools, request.temperature(), request.maxTokens());
    }

    /**
     * 内部消息 → OpenAI 兼容消息。
     */
    private OpenAiMessage toOpenAiMessage(ChatMessage message) {
        List<OpenAiToolCall> toolCalls = null;
        if (message.toolCalls() != null) {
            toolCalls = new ArrayList<>();
            for (ToolInvocation invocation : message.toolCalls()) {
                toolCalls.add(new OpenAiToolCall(invocation.id(), "function",
                        new OpenAiFunctionCall(invocation.name(), invocation.argumentsJson())));
            }
        }
        return new OpenAiMessage(roleName(message.role()), message.content(), message.name(),
                message.toolCallId(), toolCalls);
    }

    /**
     * 内部工具 → OpenAI 兼容工具。
     */
    private OpenAiTool toOpenAiTool(ToolDefinition tool) {
        JsonNode parameters;
        try {
            parameters = mapper.readTree(tool.parametersJson() == null ? "{}" : tool.parametersJson());
        } catch (Exception e) {
            parameters = mapper.createObjectNode();
        }
        return new OpenAiTool("function", new OpenAiFunction(tool.name(), tool.description(), parameters));
    }

    /**
     * OpenAI 兼容响应 → 内部结果。
     */
    private ModelTurnResult toResult(ChatCompletionResponse response, long latencyMs) {
        OpenAiMessage message = response.choices() == null || response.choices().isEmpty()
                ? new OpenAiMessage("assistant", "", null, null, null)
                : response.choices().get(0).message();

        ChatMessage internal;
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            List<ToolInvocation> invocations = new ArrayList<>();
            for (OpenAiToolCall call : message.toolCalls()) {
                invocations.add(new ToolInvocation(call.id(), call.function().name(), call.function().arguments()));
            }
            internal = ChatMessage.assistantToolCalls(invocations);
        } else {
            internal = ChatMessage.assistant(message.content());
        }
        Integer promptTokens = response.usage() == null ? null : response.usage().promptTokens();
        Integer completionTokens = response.usage() == null ? null : response.usage().completionTokens();
        return new ModelTurnResult(internal, response.model(), latencyMs, promptTokens, completionTokens);
    }

    /**
     * 角色名映射。
     */
    private static String roleName(Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
        };
    }

    /** OpenAI 兼容请求体。 */
    public record ChatCompletionRequest(String model, List<OpenAiMessage> messages, List<OpenAiTool> tools,
                                        Double temperature, @JsonProperty("max_tokens") Integer maxTokens) {
    }

    /** OpenAI 兼容消息。 */
    public record OpenAiMessage(String role, String content, String name,
                                @JsonProperty("tool_call_id") String toolCallId,
                                @JsonProperty("tool_calls") List<OpenAiToolCall> toolCalls) {
    }

    /** OpenAI 兼容工具。 */
    public record OpenAiTool(String type, OpenAiFunction function) {
    }

    /** OpenAI 兼容工具函数。 */
    public record OpenAiFunction(String name, String description, JsonNode parameters) {
    }

    /** OpenAI 兼容工具调用。 */
    public record OpenAiToolCall(String id, String type, OpenAiFunctionCall function) {
    }

    /** OpenAI 兼容工具调用函数。 */
    public record OpenAiFunctionCall(String name, String arguments) {
    }

    /** OpenAI 兼容响应。 */
    public record ChatCompletionResponse(List<Choice> choices, String model, Usage usage) {
    }

    /** OpenAI 兼容选择项。 */
    public record Choice(OpenAiMessage message) {
    }

    /** OpenAI 兼容用量。 */
    public record Usage(@JsonProperty("prompt_tokens") Integer promptTokens,
                        @JsonProperty("completion_tokens") Integer completionTokens) {
    }
}
