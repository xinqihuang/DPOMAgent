package com.dpom.agent.common.codegraph;

/**
 * 代码快照状态。
 */
public enum SnapshotStatus {
    /** 快照就绪，可查询。 */
    READY,
    /** 快照尚未就绪。 */
    NOT_READY,
    /** 快照不存在。 */
    NOT_FOUND
}
