package com.dpom.agent.common.runtime;

/**
 * 运行时证据调用超时异常。
 */
public class RuntimeEvidenceTimeoutException extends RuntimeEvidenceException {

    /**
     * 构造异常。
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public RuntimeEvidenceTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
