package com.dpom.agent.core.handoff;

import java.util.List;

/**
 * 证据包 manifest：元数据 + 全部 payload 条目（路径/校验和/大小/类别）。
 *
 * @param schemaVersion schema 版本
 * @param packageId     包标识
 * @param service       服务编码
 * @param environment   环境
 * @param release       发布版本
 * @param commit        提交 SHA
 * @param timeRange     时间窗
 * @param entries       payload 条目（确定性排序）
 */
public record PackageManifest(int schemaVersion, String packageId, String service, String environment,
                              String release, String commit, String timeRange, List<PackageEntry> entries) {
}
