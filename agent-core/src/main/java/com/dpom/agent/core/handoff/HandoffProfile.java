package com.dpom.agent.core.handoff;

/**
 * 部署 Profile：同一诊断引擎的两种装配边界，由配置显式指定，非法值启动失败。
 */
public enum HandoffProfile {
    /** 生产区域：升级判定、打包、审批、审批后上传。 */
    PRODUCTION,
    /** 研发区域：下载、校验、导入/恢复。 */
    DEVELOPMENT;

    /**
     * 解析模式字符串；非法或未知值抛异常（不静默降级）。
     *
     * @param mode 配置值
     * @return 对应 Profile
     * @throws IllegalArgumentException 非法或未知 mode
     */
    public static HandoffProfile from(String mode) {
        if (mode == null) {
            throw new IllegalArgumentException("unknown handoff mode: null");
        }
        return switch (mode) {
            case "production" -> PRODUCTION;
            case "development" -> DEVELOPMENT;
            default -> throw new IllegalArgumentException("unknown handoff mode: " + mode);
        };
    }
}
