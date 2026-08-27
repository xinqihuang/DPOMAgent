package com.dpom.agent.common.diagnosisprogress;

/**
 * 不可变进度记录的投递请求。
 *
 * @param progressId      稳定进度标识
 * @param investigationId 分区键
 * @param canonicalJson   RFC 8785 规范 JSON
 * @param canonicalSha256 规范内容的小写 SHA-256
 */
public record DiagnosisProgressDeliveryRequest(String progressId, String investigationId,
                                               String canonicalJson, String canonicalSha256) {
}
