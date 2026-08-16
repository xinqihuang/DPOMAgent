package com.dpom.agent.core.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;

import com.dpom.agent.core.handoff.EscalationReason;

/**
 * 升级判定持久化编解码：升级原因名列表 ↔ 逗号分隔串，缺失证据列表 ↔ JSON。
 */
public final class EscalationDecisionCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EscalationDecisionCodec() {
    }

    /**
     * 编码升级原因列表为逗号分隔串。
     *
     * @param reasons 升级原因列表
     * @return 逗号分隔串
     */
    public static String encodeReasons(List<EscalationReason> reasons) {
        return reasons == null ? "" : String.join(",", reasons.stream().map(Enum::name).toList());
    }

    /**
     * 解码逗号分隔串为升级原因列表。
     *
     * @param csv 逗号分隔串
     * @return 升级原因列表
     */
    public static List<EscalationReason> decodeReasons(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(EscalationReason::valueOf).toList();
    }

    /**
     * 编码缺失证据列表为 JSON。
     *
     * @param missing 缺失证据列表
     * @return JSON 串
     */
    public static String encodeMissing(List<String> missing) {
        try {
            return MAPPER.writeValueAsString(missing == null ? List.of() : missing);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化缺失证据失败", e);
        }
    }

    /**
     * 解码 JSON 为缺失证据列表。
     *
     * @param json JSON 串
     * @return 缺失证据列表
     */
    public static List<String> decodeMissing(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("解析缺失证据失败", e);
        }
    }
}
