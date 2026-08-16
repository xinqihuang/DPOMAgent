package com.dpom.agent.core.handoff;

import java.util.List;
import java.util.Map;

/**
 * 校验通过的证据包内容（已通过 schema/checksum/大小/service/release/commit 校验）。
 *
 * @param schemaVersion schema 版本
 * @param packageId     包标识
 * @param service       服务编码
 * @param environment   环境
 * @param release       发布版本
 * @param commit        提交 SHA
 * @param timeRange     时间窗
 * @param sections      section -> 条目
 */
public record RecoveredEvidencePackage(int schemaVersion, String packageId, String service, String environment,
                                       String release, String commit, String timeRange,
                                       Map<String, List<String>> sections) {
}
