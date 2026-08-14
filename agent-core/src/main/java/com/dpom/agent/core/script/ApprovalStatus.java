package com.dpom.agent.core.script;

/**
 * 审批状态。
 */
public enum ApprovalStatus {
    /** 无需审批（只读诊断）。 */
    NONE_REQUIRED,
    /** 需要审批（修复脚本）。 */
    REQUIRES_APPROVAL
}
