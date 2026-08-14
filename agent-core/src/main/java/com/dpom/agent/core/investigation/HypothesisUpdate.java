package com.dpom.agent.core.investigation;

import com.dpom.agent.core.hypothesis.HypothesisStatus;

/**
 * 一次假设状态更新。
 *
 * @param hypothesisId 假设 id（仅更新已有假设，非空）
 * @param newStatus    新状态
 */
public record HypothesisUpdate(Long hypothesisId, HypothesisStatus newStatus) {
}
