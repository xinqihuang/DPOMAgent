package com.dpom.agent.core.handoff;

/**
 * 证据交接配置：部署 Profile、升级阈值、证据包上限、schema 版本、OBS 意图/allow-list 与审批有效期。
 *
 * @param profile             部署 Profile（production / development）
 * @param confidenceThreshold 置信度阈值（0–100，低于则可能升级）
 * @param maxPackageBytes     证据包总字节上限
 * @param maxPackageEntries   证据包条目数上限
 * @param schemaVersion       支持的证据包 schema 版本
 * @param obsEnabled          是否启用 OBS 传输（true 但无真实 adapter 时 fail closed）
 * @param allowedBucket       OBS allow-list bucket（空表示未配置）
 * @param allowedPrefix       OBS allow-list prefix（空表示未配置）
 * @param approvalTtlSeconds  审批有效期（秒）
 */
public record HandoffConfig(HandoffProfile profile, int confidenceThreshold, int maxPackageBytes,
                            int maxPackageEntries, int schemaVersion, boolean obsEnabled, String allowedBucket,
                            String allowedPrefix, int approvalTtlSeconds) {

    /**
     * 默认配置（development，OBS 未启用）。
     *
     * @return 默认配置
     */
    public static HandoffConfig defaults() {
        return new HandoffConfig(HandoffProfile.DEVELOPMENT, 60, 1_048_576, 200, 1, false, "", "", 3600);
    }

    /**
     * OBS allow-list 是否已配置（bucket 与 prefix 均非空）。
     *
     * @return true 表示可上传
     */
    public boolean isObsConfigured() {
        return allowedBucket != null && !allowedBucket.isBlank() && allowedPrefix != null && !allowedPrefix.isBlank();
    }

    /**
     * 是否为 production Profile。
     *
     * @return true 表示生产侧
     */
    public boolean isProduction() {
        return profile == HandoffProfile.PRODUCTION;
    }
}
