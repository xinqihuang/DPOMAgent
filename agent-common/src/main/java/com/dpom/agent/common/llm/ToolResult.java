package com.dpom.agent.common.llm;

/**
 * 工具执行结果，回传给模型继续推理。
 *
 * @param toolCallId 对应的工具调用 id
 * @param content    结果内容（成功时非空）
 * @param error      错误信息（失败时非空）
 */
public record ToolResult(String toolCallId, String content, String error) {
}
