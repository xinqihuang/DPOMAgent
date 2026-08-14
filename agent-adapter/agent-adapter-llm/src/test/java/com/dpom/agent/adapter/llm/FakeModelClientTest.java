package com.dpom.agent.adapter.llm;

import com.dpom.agent.common.llm.ChatMessage;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.llm.ModelProviderException;
import com.dpom.agent.common.llm.ModelTimeoutException;
import com.dpom.agent.common.llm.ModelTurnRequest;
import com.dpom.agent.common.llm.ModelTurnResult;
import com.dpom.agent.common.llm.Role;
import com.dpom.agent.common.llm.ToolDefinition;
import com.dpom.agent.common.llm.ToolInvocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 假模型客户端验收测试：普通回答、工具调用、工具结果后继续、超时与错误映射。
 */
class FakeModelClientTest {

    /**
     * 普通文本回答。
     */
    @Test
    void answersNormalText() {
        ModelClient client = new FakeModelClient(
                request -> new ModelTurnResult(ChatMessage.assistant("设备创建成功但未落库")));

        ModelTurnResult result = client.complete(
                new ModelTurnRequest(List.of(ChatMessage.user("症状是什么")), List.of()));

        assertThat(result.message().role()).isEqualTo(Role.ASSISTANT);
        assertThat(result.message().content()).isEqualTo("设备创建成功但未落库");
    }

    /**
     * 返回工具调用。
     */
    @Test
    void returnsToolCalls() {
        ToolDefinition tool = new ToolDefinition("search_logs", "搜索日志", "{}");
        ModelClient client = new FakeModelClient(request -> new ModelTurnResult(
                ChatMessage.assistantToolCalls(List.of(
                        new ToolInvocation("call-1", "search_logs", "{\"query\":\"INSERT\"}")))));

        ModelTurnResult result = client.complete(
                new ModelTurnRequest(List.of(ChatMessage.user("查日志")), List.of(tool)));

        assertThat(result.message().toolCalls()).hasSize(1);
        assertThat(result.message().toolCalls().get(0).name()).isEqualTo("search_logs");
    }

    /**
     * 工具结果回传后继续推理。
     */
    @Test
    void continuesAfterToolResult() {
        AtomicInteger turn = new AtomicInteger(0);
        ModelClient client = new FakeModelClient(request -> {
            if (turn.incrementAndGet() == 1) {
                return new ModelTurnResult(ChatMessage.assistantToolCalls(List.of(
                        new ToolInvocation("call-1", "read_source", "{\"file\":\"AssetRepository.java\"}"))));
            }
            boolean hasToolResult = request.messages().stream()
                    .anyMatch(message -> message.role() == Role.TOOL && "call-1".equals(message.toolCallId()));
            return new ModelTurnResult(ChatMessage.assistant(hasToolResult ? "已确认：事务回滚" : "缺少工具结果"));
        });

        ModelTurnResult first = client.complete(
                new ModelTurnRequest(List.of(ChatMessage.user("分析")), List.of()));
        assertThat(first.message().toolCalls()).hasSize(1);

        ModelTurnResult second = client.complete(new ModelTurnRequest(
                List.of(ChatMessage.user("分析"), first.message(), ChatMessage.tool("call-1", "源码内容")),
                List.of()));
        assertThat(second.message().content()).isEqualTo("已确认：事务回滚");
    }

    /**
     * 超时与 Provider 错误映射。
     */
    @Test
    void mapsTimeoutAndProviderErrors() {
        ModelClient timeout = new FakeModelClient(request -> {
            throw new ModelTimeoutException("超时");
        });
        assertThatThrownBy(() -> timeout.complete(
                new ModelTurnRequest(List.of(ChatMessage.user("x")), List.of())))
                .isInstanceOf(ModelTimeoutException.class);

        ModelClient error = new FakeModelClient(request -> {
            throw new ModelProviderException("服务不可用");
        });
        assertThatThrownBy(() -> error.complete(
                new ModelTurnRequest(List.of(ChatMessage.user("x")), List.of())))
                .isInstanceOf(ModelProviderException.class);
    }
}
