package com.dpom.agent.core.diagnosisevent;

import com.dpom.agent.common.diagnosisevent.DiagnosisEvent;

import java.util.Arrays;

/**
 * 已验证、规范化并计算摘要的事件。
 *
 * @param event           领域事件
 * @param canonicalBytes  RFC 8785 UTF-8 字节
 * @param canonicalSha256 小写 SHA-256
 */
public record BuiltDiagnosisEvent(DiagnosisEvent event, byte[] canonicalBytes, String canonicalSha256) {

    /**
     * 防御性复制规范字节。
     */
    public BuiltDiagnosisEvent {
        canonicalBytes = canonicalBytes == null ? null : Arrays.copyOf(canonicalBytes, canonicalBytes.length);
    }

    @Override
    public byte[] canonicalBytes() {
        return Arrays.copyOf(canonicalBytes, canonicalBytes.length);
    }
}
