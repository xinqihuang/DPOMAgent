package com.dpom.agent.alarm.query;

import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.SeverityLevel;

import java.util.function.Consumer;

/**
 * 告警订阅：注册过滤条件与回调，治理完成后异步推送匹配的告警。
 *
 * <p>过滤字段为空表示不限。回调在虚拟线程上异步执行，不阻塞接入路径。</p>
 *
 * @param source     来源过滤（可为空）
 * @param serviceCode 服务过滤（可为空）
 * @param severity   严重度过滤（可为空）
 * @param callback   回调
 */
public record AlarmSubscription(AlarmSource source, String serviceCode, SeverityLevel severity,
                                Consumer<com.dpom.agent.alarm.domain.Alarm> callback) {

    /**
     * 判断告警是否匹配本订阅过滤条件。
     *
     * @param alarm 告警
     * @return 匹配返回 true
     */
    public boolean matches(com.dpom.agent.alarm.domain.Alarm alarm) {
        if (source != null && source != alarm.source()) {
            return false;
        }
        if (serviceCode != null && !serviceCode.equals(alarm.serviceCode())) {
            return false;
        }
        return severity == null || severity == alarm.severity();
    }
}
