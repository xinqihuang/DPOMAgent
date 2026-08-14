package com.dpom.agent.common.llm;

import java.util.List;

/**
 * 聊天消息。
 *
 * @param role      角色
 * @param content   文本内容（可为空）
 * @param name      名称（可为空）
 * @param toolCallId 工具调用 id（TOOL 角色使用，可为空）
 * @param toolCalls 工具调用列表（ASSISTANT 角色发起调用时非空）
 */
public record ChatMessage(Role role, String content, String name, String toolCallId,
                          List<ToolInvocation> toolCalls) {

    /**
     * 构造系统消息。
     *
     * @param content 内容
     * @return 系统消息
     */
    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content, null, null, null);
    }

    /**
     * 构造用户消息。
     *
     * @param content 内容
     * @return 用户消息
     */
    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content, null, null, null);
    }

    /**
     * 构造助手文本消息。
     *
     * @param content 内容
     * @return 助手消息
     */
    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content, null, null, null);
    }

    /**
     * 构造助手工具调用消息。
     *
     * @param toolCalls 工具调用列表
     * @return 助手消息
     */
    public static ChatMessage assistantToolCalls(List<ToolInvocation> toolCalls) {
        return new ChatMessage(Role.ASSISTANT, null, null, null, toolCalls);
    }

    /**
     * 构造工具结果消息。
     *
     * @param toolCallId 工具调用 id
     * @param content    结果内容
     * @return 工具消息
     */
    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage(Role.TOOL, content, null, toolCallId, null);
    }
}
