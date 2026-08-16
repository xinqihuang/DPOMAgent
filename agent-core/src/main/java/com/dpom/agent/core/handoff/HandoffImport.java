package com.dpom.agent.core.handoff;

import java.time.LocalDateTime;

/**
 * 研发侧导入记录：用于重复导入幂等（package_id 唯一）。
 *
 * @param id        主键
 * @param packageId 包标识（唯一）
 * @param service   服务编码
 * @param release   发布版本
 * @param commit    提交 SHA
 * @param createdAt 创建时间
 */
public record HandoffImport(Long id, String packageId, String service, String release, String commit,
                            LocalDateTime createdAt) {
}
