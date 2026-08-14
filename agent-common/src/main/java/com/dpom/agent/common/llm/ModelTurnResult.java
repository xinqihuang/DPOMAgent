package com.dpom.agent.common.llm;

/**
 * 一次模型推理结果。
 *
 * @param message          助手返回消息（含最终文本或工具调用）
 * @param model            实际使用的模型（可为空）
 * @param latencyMs        推理耗时毫秒（可为空）
 * @param promptTokens     Prompt token 数（可为空）
 * @param completionTokens 输出 token 数（可为空）
 */
public record ModelTurnResult(ChatMessage message, String model, Long latencyMs,
                              Integer promptTokens, Integer completionTokens) {

    /**
     * 便捷构造：仅指定返回消息。
     *
     * @param message 助手消息
     */
    public ModelTurnResult(ChatMessage message) {
        this(message, null, null, null, null);
    }
}
