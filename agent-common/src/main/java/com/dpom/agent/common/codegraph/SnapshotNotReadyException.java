package com.dpom.agent.common.codegraph;

/**
 * 快照未就绪异常。
 */
public class SnapshotNotReadyException extends CodeGraphException {

    /**
     * 构造异常。
     *
     * @param message 错误信息
     */
    public SnapshotNotReadyException(String message) {
        super(message);
    }
}
