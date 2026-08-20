package com.dpom.agent.common.alarm;

/**
 * 告警来源服务：华为云多服务告警的统一来源标识。
 */
public enum AlarmSource {

    /** 应用运维管理。 */
    AOM,
    /** 云监控服务。 */
    CES,
    /** 应用性能管理。 */
    APM,
    /** 日志服务。 */
    LTS
}
