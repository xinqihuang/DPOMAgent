package com.dpom.agent.common.llm;

/**
 * 模型请求调用的一次工具调用。
 *
 * @param id            工具调用 id
 * @param name          工具名
 * @param argumentsJson 工具参数（JSON 字符串）
 */
public record ToolInvocation(String id, String name, String argumentsJson) {
}
