package com.dpom.agent.common.codegraph;

/**
 * 代码图异常基类。
 */
public class CodeGraphException extends RuntimeException {

    /**
     * 构造异常。
     *
     * @param message 错误信息
     */
    public CodeGraphException(String message) {
        super(message);
    }

    /**
     * 构造异常。
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public CodeGraphException(String message, Throwable cause) {
        super(message, cause);
    }
}
