package com.dpom.agent.common.diagnosisevent;

/**
 * 被诊断源码的来源版本。
 *
 * @param status      available 或 unavailable
 * @param serviceCode 服务编码
 * @param release     发布版本
 * @param commitSha   提交摘要
 * @param reasonCode  不可用时的稳定原因码
 */
public record ProvenanceSource(String status, String serviceCode, String release, String commitSha,
                               String reasonCode) {

    /**
     * 构造可用源码版本。
     *
     * @param serviceCode 服务编码
     * @param release     发布版本
     * @param commitSha   提交摘要
     * @return 可用源码版本
     */
    public static ProvenanceSource available(String serviceCode, String release, String commitSha) {
        return new ProvenanceSource("available", serviceCode, release, commitSha, null);
    }

    /**
     * 构造不可用源码版本。
     *
     * @param reasonCode 稳定原因码
     * @return 不可用源码版本
     */
    public static ProvenanceSource unavailable(String reasonCode) {
        return new ProvenanceSource("unavailable", null, null, null, reasonCode);
    }
}
