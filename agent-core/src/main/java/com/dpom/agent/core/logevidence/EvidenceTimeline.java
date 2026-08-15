package com.dpom.agent.core.logevidence;

import java.util.List;

/**
 * 证据时间线视图：release、逐条证据元数据、降级与结论引用。
 *
 * @param release                发布版本
 * @param entries                证据条目
 * @param degradations           降级标记
 * @param conclusionResultType   结论类型（可空）
 * @param conclusionEvidenceIds  结论引用证据 id（可空）
 */
public record EvidenceTimeline(String release, List<EvidenceTimelineEntry> entries, List<String> degradations,
                               String conclusionResultType, String conclusionEvidenceIds) {
}
