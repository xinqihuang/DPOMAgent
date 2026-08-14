package com.dpom.agent.common.llm;

/**
 * 与具体模型 Provider 无关的模型客户端契约。
 *
 * <p>Core 只依赖本接口，不依赖任何 Provider SDK DTO。实现位于 agent-adapter-llm。</p>
 */
public interface ModelClient {

    /**
     * 执行一次推理。
     *
     * @param request 推理请求（含完整消息历史与可选工具）
     * @return 推理结果（最终文本或工具调用）
     * @throws ModelTimeoutException 超时
     * @throws ModelProviderException Provider 错误
     */
    ModelTurnResult complete(ModelTurnRequest request);
}
