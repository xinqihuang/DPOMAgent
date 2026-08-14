package com.dpom.agent.core.workspace;

/**
 * 工作区访问被拒绝（路径越界、符号链接逃逸或文件不可访问）。
 */
public class WorkspaceAccessException extends WorkspaceException {

    /**
     * 构造异常。
     *
     * @param message 错误信息
     */
    public WorkspaceAccessException(String message) {
        super(message);
    }

    /**
     * 构造异常。
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public WorkspaceAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
