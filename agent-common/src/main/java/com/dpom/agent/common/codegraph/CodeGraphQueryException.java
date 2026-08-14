package com.dpom.agent.common.codegraph;

/**
 * 代码图查询失败异常。
 */
public class CodeGraphQueryException extends CodeGraphException {

    /**
     * 构造异常。
     *
     * @param message 错误信息
     */
    public CodeGraphQueryException(String message) {
        super(message);
    }

    /**
     * 构造异常。
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public CodeGraphQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
