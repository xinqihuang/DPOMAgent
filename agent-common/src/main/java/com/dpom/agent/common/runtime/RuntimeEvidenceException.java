package com.dpom.agent.common.runtime;

/**
 * 运行时证据异常基类。
 */
public class RuntimeEvidenceException extends RuntimeException {

    /**
     * 构造异常。
     *
     * @param message 错误信息
     */
    public RuntimeEvidenceException(String message) {
        super(message);
    }

    /**
     * 构造异常。
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public RuntimeEvidenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
