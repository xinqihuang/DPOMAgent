package com.dpom.agent.core.handoff;

import java.util.List;
import java.util.Map;

/**
 * 诊断证据包内容：已脱敏、已过 allow-list、已限量的逻辑内容，序列化前的中转。
 *
 * @param schemaVersion    schema 版本
 * @param packageId        包标识（服务生成的不可预测 id）
 * @param service          服务编码
 * @param environment      环境
 * @param release          发布版本
 * @param commit           提交 SHA
 * @param timeRange        时间窗
 * @param sections         allow-list section -> 条目（已脱敏）
 * @param redactionCounts  section -> 脱敏标记计数（用于脱敏报告）
 */
public record DiagnosticEvidencePackage(int schemaVersion, String packageId, String service, String environment,
                                        String release, String commit, String timeRange,
                                        Map<String, List<String>> sections, Map<String, Integer> redactionCounts) {
}
