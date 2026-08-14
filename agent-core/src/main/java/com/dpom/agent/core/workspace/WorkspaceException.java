package com.dpom.agent.core.workspace;

/**
 * 工作区访问异常基类。
 */
public class WorkspaceException extends RuntimeException {

    /**
     * 构造异常。
     *
     * @param message 错误信息
     */
    public WorkspaceException(String message) {
        super(message);
    }

    /**
     * 构造异常。
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public WorkspaceException(String message, Throwable cause) {
        super(message, cause);
    }
}
