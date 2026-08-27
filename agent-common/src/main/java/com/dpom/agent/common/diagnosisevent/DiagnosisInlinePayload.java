package com.dpom.agent.common.diagnosisevent;

import java.util.Map;

/**
 * 内联诊断载荷。
 *
 * @param payloadType          载荷类型
 * @param payloadSchemaVersion 载荷结构版本
 * @param content              有界结构化内容
 */
public record DiagnosisInlinePayload(String payloadType, String payloadSchemaVersion, Map<String, Object> content) {

    /**
     * 防御性复制内容顶层映射。
     */
    public DiagnosisInlinePayload {
        content = content == null ? null : Map.copyOf(content);
    }
}
