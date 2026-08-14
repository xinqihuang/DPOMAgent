package com.dpom.agent.common.llm;

/**
 * 暴露给模型的工具定义。
 *
 * @param name          工具名
 * @param description   工具描述
 * @param parametersJson 参数 JSON Schema（JSON 字符串）
 */
public record ToolDefinition(String name, String description, String parametersJson) {
}
