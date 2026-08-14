package com.dpom.agent.common.llm;

import java.util.List;

/**
 * 一次模型推理请求（包含完整消息历史与可选工具定义）。
 *
 * @param messages    消息历史
 * @param tools       可用工具定义（可为空）
 * @param model       模型名（可为空，由 Provider 决定默认值）
 * @param temperature 采样温度（可为空）
 * @param maxTokens   最大输出 token（可为空）
 */
public record ModelTurnRequest(List<ChatMessage> messages, List<ToolDefinition> tools, String model,
                               Double temperature, Integer maxTokens) {

    /**
     * 便捷构造：仅指定消息与工具。
     *
     * @param messages 消息历史
     * @param tools    工具定义
     */
    public ModelTurnRequest(List<ChatMessage> messages, List<ToolDefinition> tools) {
        this(messages, tools, null, null, null);
    }
}
