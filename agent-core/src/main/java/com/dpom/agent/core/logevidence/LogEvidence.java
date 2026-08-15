package com.dpom.agent.core.logevidence;

import java.util.List;

/**
 * 一条可审计的日志证据：把模板摘要与 incident/版本身份、来源与截断信息绑定，供调查循环与 LLM 消费。
 *
 * @param evidenceId   证据 id
 * @param summary      模板摘要
 * @param service      服务编码
 * @param environment  环境
 * @param release      发布版本
 * @param commit       提交 SHA
 * @param timeRange    时间范围
 * @param traceIds     关联 trace id（可为空）
 * @param minerVersion 模板挖掘器版本
 * @param provenance   来源与版本元数据
 */
public record LogEvidence(String evidenceId, LogTemplateSummary summary, String service, String environment,
                          String release, String commit, String timeRange, List<String> traceIds,
                          String minerVersion, EvidenceProvenance provenance) {
}
