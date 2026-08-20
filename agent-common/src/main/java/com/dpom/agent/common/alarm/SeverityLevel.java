package com.dpom.agent.common.alarm;

/**
 * 统一严重度等级：各源告警严重度经分级映射后的归一化结果。
 */
public enum SeverityLevel {

    /** 紧急：最高等级。 */
    CRITICAL,
    /** 警告。 */
    WARNING,
    /** 提示：最低等级。 */
    INFO
}
