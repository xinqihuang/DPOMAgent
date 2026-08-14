package com.dpom.agent.common.codegraph;

/**
 * 代码图调用超时异常。
 */
public class CodeGraphTimeoutException extends CodeGraphException {

    /**
     * 构造异常。
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public CodeGraphTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
