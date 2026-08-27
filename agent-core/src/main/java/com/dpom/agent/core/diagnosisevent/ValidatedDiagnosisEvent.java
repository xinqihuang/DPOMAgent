package com.dpom.agent.core.diagnosisevent;

import java.util.Arrays;

/**
 * 契约验证后的规范事件内容。
 *
 * @param canonicalBytes  规范 UTF-8 字节
 * @param canonicalSha256 小写 SHA-256
 */
public record ValidatedDiagnosisEvent(byte[] canonicalBytes, String canonicalSha256) {

    /**
     * 防御性复制规范字节。
     */
    public ValidatedDiagnosisEvent {
        canonicalBytes = Arrays.copyOf(canonicalBytes, canonicalBytes.length);
    }

    @Override
    public byte[] canonicalBytes() {
        return Arrays.copyOf(canonicalBytes, canonicalBytes.length);
    }
}
