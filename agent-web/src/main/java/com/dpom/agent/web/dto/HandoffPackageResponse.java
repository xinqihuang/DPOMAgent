package com.dpom.agent.web.dto;

/**
 * 证据包构建响应。
 *
 * @param packageId 包标识
 * @param checksum  ZIP SHA-256 校验和
 * @param sizeBytes ZIP 字节大小
 */
public record HandoffPackageResponse(String packageId, String checksum, long sizeBytes) {
}
