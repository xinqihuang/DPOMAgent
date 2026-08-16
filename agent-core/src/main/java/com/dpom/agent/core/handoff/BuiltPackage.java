package com.dpom.agent.core.handoff;

/**
 * 构建完成的证据包。
 *
 * @param packageId 包标识
 * @param checksum  ZIP SHA-256 校验和
 * @param sizeBytes ZIP 字节大小
 * @param zipBytes  ZIP 字节
 */
public record BuiltPackage(String packageId, String checksum, long sizeBytes, byte[] zipBytes) {
}
