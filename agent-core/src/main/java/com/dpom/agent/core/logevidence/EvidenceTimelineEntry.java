package com.dpom.agent.core.logevidence;

/**
 * 证据时间线条目：audit 视角下的单条证据元数据。
 *
 * @param evidenceId        证据 id
 * @param type              类型（LOG / SOURCE）
 * @param provenanceSource  来源（drain3 / codegraph）
 * @param release           发布版本
 * @param commit            提交 SHA
 * @param ruleVersion       规则版本（可空）
 * @param minerVersion      挖掘器版本（可空）
 * @param truncated         是否截断
 * @param degradation       降级/状态标记（可空）
 */
public record EvidenceTimelineEntry(String evidenceId, String type, String provenanceSource, String release,
                                    String commit, String ruleVersion, String minerVersion, boolean truncated,
                                    String degradation) {
}
