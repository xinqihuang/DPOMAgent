package com.dpom.agent.core.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.dpom.agent.core.logevidence.EvidenceBundle;

/**
 * 证据束持久化编解码：EvidenceBundle ↔ JSON。
 */
public final class EvidenceBundleCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EvidenceBundleCodec() {
    }

    /**
     * 序列化证据束为 JSON。
     *
     * @param bundle 证据束
     * @return JSON 串
     */
    public static String encode(EvidenceBundle bundle) {
        try {
            return MAPPER.writeValueAsString(bundle);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化证据束失败", e);
        }
    }

    /**
     * 反序列化 JSON 为证据束。
     *
     * @param json JSON 串
     * @return 证据束
     */
    public static EvidenceBundle decode(String json) {
        try {
            return MAPPER.readValue(json, EvidenceBundle.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("解析证据束失败", e);
        }
    }
}
