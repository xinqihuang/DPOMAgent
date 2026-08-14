package com.dpom.agent.common.runtime;

/**
 * 运行时证据查询失败异常。
 */
public class RuntimeEvidenceQueryException extends RuntimeEvidenceException {

    /**
     * 构造异常。
     *
     * @param message 错误信息
     */
    public RuntimeEvidenceQueryException(String message) {
        super(message);
    }

    /**
     * 构造异常。
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public RuntimeEvidenceQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
