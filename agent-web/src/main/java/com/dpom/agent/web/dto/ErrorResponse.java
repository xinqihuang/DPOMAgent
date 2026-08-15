package com.dpom.agent.web.dto;

/**
 * 稳定错误响应：不含 Java 类名、堆栈、SQL、路径、secret。
 */
public record ErrorResponse(String code, String message, Long investigationId) {

    /** 便捷构造（无调查 id）。 */
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null);
    }
}
