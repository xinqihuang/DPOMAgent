package com.dpom.agent.core.handoff;

/**
 * 证据交接稳定错误码（有限枚举，用于结构化错误与审计，不携带敏感值）。
 */
public enum HandoffErrorCode {
    /** 不满足升级条件。 */
    NOT_ELIGIBLE,
    /** 未批准上传。 */
    NOT_APPROVED,
    /** 审批已过期或失效。 */
    APPROVAL_EXPIRED,
    /** OBS 传输禁用。 */
    OBS_DISABLED,
    /** OBS 已启用但真实 adapter 不存在。 */
    OBS_ADAPTER_UNAVAILABLE,
    /** OBS allow-list 未配置或不允许。 */
    OBS_NOT_CONFIGURED,
    /** schema 版本不支持。 */
    SCHEMA_UNSUPPORTED,
    /** 校验和不匹配。 */
    CHECKSUM_MISMATCH,
    /** 超过大小上限。 */
    SIZE_EXCEEDED,
    /** 超过条目数上限。 */
    ENTRIES_EXCEEDED,
    /** 包含禁止字段（源码/凭据）。 */
    FORBIDDEN_CONTENT,
    /** service/release/commit 不匹配。 */
    VERSION_MISMATCH,
    /** 证据包结构非法。 */
    PACKAGE_INVALID,
    /** 传输失败。 */
    STORE_FAILURE,
    /** 入参非法。 */
    INVALID_ARGUMENT
}
