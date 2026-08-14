package com.dpom.agent.adapter.llm;

import com.dpom.agent.common.llm.ChatMessage;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.llm.ModelTurnRequest;
import com.dpom.agent.common.llm.ModelTurnResult;

import java.util.Objects;
import java.util.function.Function;

/**
 * 基于响应函数的假模型客户端。
 *
 * <p>用于单元测试与本地开发，隔离真实模型 Provider；由调用方提供确定性响应函数。</p>
 */
public class FakeModelClient implements ModelClient {

    private final Function<ModelTurnRequest, ModelTurnResult> responder;

    /**
     * 构造假客户端。
     *
     * @param responder 响应函数，接收请求并返回结果（可抛出超时/Provider 异常）
     */
    public FakeModelClient(Function<ModelTurnRequest, ModelTurnResult> responder) {
        this.responder = Objects.requireNonNull(responder, "responder");
    }

    @Override
    public ModelTurnResult complete(ModelTurnRequest request) {
        return responder.apply(request);
    }

    /**
     * 创建始终回复固定文本的客户端。
     *
     * @param text 固定回复文本
     * @return 假客户端
     */
    public static FakeModelClient answering(String text) {
        return new FakeModelClient(request -> new ModelTurnResult(ChatMessage.assistant(text)));
    }
}
