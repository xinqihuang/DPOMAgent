package com.dpom.agent.common.codegraph;

/**
 * 快照不存在异常。
 */
public class SnapshotNotFoundException extends CodeGraphException {

    /**
     * 构造异常。
     *
     * @param message 错误信息
     */
    public SnapshotNotFoundException(String message) {
        super(message);
    }
}
