package com.dpom.agent.core.handoff;

import com.dpom.agent.core.hypothesis.HypothesisStatus;

import java.util.List;

/**
 * 升级判定输入：从既有调查状态（结论/假设/证据束）确定性投影而来，不含 LLM 输出或远端 DTO。
 *
 * @param resultType             结论类型（可为空）
 * @param highestHypothesisStatus 最高假设状态（可为空）
 * @param hasVerifiedSource      是否存在已验证源码证据
 * @param contradictions         矛盾标记
 * @param missingEvidence        缺失证据标记
 * @param requiresCodeProof      是否需要代码级证明
 */
public record EscalationContext(String resultType, HypothesisStatus highestHypothesisStatus,
                                boolean hasVerifiedSource, List<String> contradictions,
                                List<String> missingEvidence, boolean requiresCodeProof) {
}
