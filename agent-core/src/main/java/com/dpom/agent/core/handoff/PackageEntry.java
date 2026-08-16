package com.dpom.agent.core.handoff;

/**
 * 证据包条目：固定路径 + SHA-256 校验和 + 字节大小 + 类别。
 *
 * @param path     条目路径（服务自有固定路径）
 * @param checksum SHA-256 十六进制
 * @param size     字节大小
 * @param category 类别（对应 allow-list section 或 security）
 */
public record PackageEntry(String path, String checksum, long size, String category) {
}
