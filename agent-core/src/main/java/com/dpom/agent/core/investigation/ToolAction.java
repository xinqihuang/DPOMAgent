package com.dpom.agent.core.investigation;

/**
 * 一个受限工具动作：每轮只执行一个。
 *
 * @param name      工具名
 * @param inputJson 工具入参（JSON 字符串）
 * @param summary   动作摘要
 */
public record ToolAction(String name, String inputJson, String summary) {
}
