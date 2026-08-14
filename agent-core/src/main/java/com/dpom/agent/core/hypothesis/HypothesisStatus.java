package com.dpom.agent.core.hypothesis;

/**
 * 假设状态。
 */
public enum HypothesisStatus {
    /** 已提出。 */
    PROPOSED,
    /** 验证中。 */
    VALIDATING,
    /** 已验证。 */
    VALIDATED,
    /** 已证伪（保留否定证据）。 */
    INVALIDATED,
    /** 无法定论。 */
    INCONCLUSIVE
}
