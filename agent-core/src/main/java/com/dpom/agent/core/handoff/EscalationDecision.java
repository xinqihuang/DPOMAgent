package com.dpom.agent.core.handoff;

import java.util.List;

/**
 * 升级判定结果：确定性纯函数输出，绝不触发上传。
 *
 * @param eligible        是否满足升级条件
 * @param reasons         升级原因（有限枚举）
 * @param missingEvidence 缺失证据标记（可为空）
 * @param confidence      置信度（0–100）
 */
public record EscalationDecision(boolean eligible, List<EscalationReason> reasons, List<String> missingEvidence,
                                 int confidence) {
}
