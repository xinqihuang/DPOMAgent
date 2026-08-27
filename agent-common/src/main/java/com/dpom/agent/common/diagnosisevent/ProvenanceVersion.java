package com.dpom.agent.common.diagnosisevent;

/**
 * 应用、模型、提示词、技能或工具契约的版本维度。
 *
 * @param status     available 或 unavailable
 * @param name       可用时的名称
 * @param version    可用时的版本
 * @param provider   可选提供方
 * @param reasonCode 不可用时的稳定原因码
 */
public record ProvenanceVersion(String status, String name, String version, String provider, String reasonCode) {

    /**
     * 构造可用版本。
     *
     * @param name     名称
     * @param version 版本
     * @param provider 可选提供方
     * @return 可用版本
     */
    public static ProvenanceVersion available(String name, String version, String provider) {
        return new ProvenanceVersion("available", name, version, provider, null);
    }

    /**
     * 构造不可用版本。
     *
     * @param reasonCode 稳定原因码
     * @return 不可用版本
     */
    public static ProvenanceVersion unavailable(String reasonCode) {
        return new ProvenanceVersion("unavailable", null, null, null, reasonCode);
    }
}
