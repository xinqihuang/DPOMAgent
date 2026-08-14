package com.dpom.agent.common.llm;

/**
 * 聊天消息角色。
 */
public enum Role {
    /** 系统提示。 */
    SYSTEM,
    /** 用户消息。 */
    USER,
    /** 助手（模型）消息。 */
    ASSISTANT,
    /** 工具执行结果消息。 */
    TOOL
}
