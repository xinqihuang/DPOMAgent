package com.dpom.agent.core.handoff;

import com.dpom.agent.core.logevidence.EvidenceBundle;

/**
 * 研发侧导入结果。
 *
 * @param packageId      包标识
 * @param alreadyImported 是否已导入（幂等命中）
 * @param bundle         恢复后的证据束
 */
public record ImportResult(String packageId, boolean alreadyImported, EvidenceBundle bundle) {
}
