package com.dpom.agent.core.handoff;

/**
 * 上传批准状态：与升级判定（eligible）分离的独立持久化决策。
 */
public enum UploadApprovalStatus {
    /** 未批准（默认，绝不上传）。 */
    NOT_APPROVED,
    /** 已批准（允许一次上传）。 */
    APPROVED,
    /** 已拒绝。 */
    REJECTED
}
