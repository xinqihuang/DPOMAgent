package com.dpom.agent.web.dto;

import java.util.List;

/**
 * 升级判定响应（纯 DTO，不含领域类型）。
 *
 * @param eligible        是否满足升级条件
 * @param reasons         升级原因（稳定枚举名）
 * @param missingEvidence 缺失证据标记
 * @param confidence      置信度（0–100）
 */
public record EscalationDecisionResponse(boolean eligible, List<String> reasons, List<String> missingEvidence,
                                         int confidence) {
}
