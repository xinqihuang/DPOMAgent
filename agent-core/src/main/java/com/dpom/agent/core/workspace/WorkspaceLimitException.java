package com.dpom.agent.core.workspace;

/**
 * 工作区读取超限异常（文件过大等）。
 */
public class WorkspaceLimitException extends WorkspaceException {

    /**
     * 构造异常。
     *
     * @param message 错误信息
     */
    public WorkspaceLimitException(String message) {
        super(message);
    }
}
