package com.dpom.agent.core.handoff;

/**
 * 升级原因（有限枚举，用于确定性判定与低基数指标）。
 */
public enum EscalationReason {
    /** 置信度低于阈值。 */
    LOW_CONFIDENCE,
    /** 存在未解决矛盾。 */
    UNRESOLVED_CONTRADICTION,
    /** 缺失必要证据。 */
    MISSING_EVIDENCE,
    /** 需要代码级证明（生产侧无源码）。 */
    CODE_PROOF_REQUIRED
}
