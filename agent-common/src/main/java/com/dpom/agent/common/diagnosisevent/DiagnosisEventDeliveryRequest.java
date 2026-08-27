package com.dpom.agent.common.diagnosisevent;

/**
 * 不可变事件的投递请求。
 *
 * @param eventId         事件标识
 * @param idempotencyKey  幂等键
 * @param canonicalJson   RFC 8785 规范 JSON
 * @param canonicalSha256 规范内容的小写 SHA-256
 */
public record DiagnosisEventDeliveryRequest(String eventId, String idempotencyKey, String canonicalJson,
                                            String canonicalSha256) {
}
