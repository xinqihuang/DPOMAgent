package com.dpom.agent.core.investigation;

/**
 * 调查生命周期状态。
 *
 * <p>正常推进：CREATED → SCOPING → RESEARCHING → FORMING_HYPOTHESES → VALIDATING → SYNTHESIZING → COMPLETED。</p>
 * <p>等待人工：WAITING_FOR_HUMAN；终态：INCONCLUSIVE / FAILED / CANCELLED。</p>
 */
public enum InvestigationStatus {
    /** 已创建，尚未开始。 */
    CREATED,
    /** 正在界定调查范围。 */
    SCOPING,
    /** 正在收集证据。 */
    RESEARCHING,
    /** 正在形成候选假设。 */
    FORMING_HYPOTHESES,
    /** 正在验证假设。 */
    VALIDATING,
    /** 正在综合结论。 */
    SYNTHESIZING,
    /** 调查完成。 */
    COMPLETED,
    /** 等待人工反馈。 */
    WAITING_FOR_HUMAN,
    /** 证据不足，无法定论。 */
    INCONCLUSIVE,
    /** 调查失败。 */
    FAILED,
    /** 已取消。 */
    CANCELLED
}
