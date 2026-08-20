package com.dpom.agent.alarm.governance;

import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.SeverityLevel;

/**
 * 严重度分级规则：来源原始严重度到统一严重度的映射条目。
 *
 * @param source      来源服务
 * @param rawSeverity 原始严重度
 * @param unified     统一严重度
 */
public record SeverityGradingRule(AlarmSource source, String rawSeverity, SeverityLevel unified) {
}
