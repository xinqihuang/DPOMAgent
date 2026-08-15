package com.dpom.agent.core.logevidence;

import java.util.List;
import java.util.Map;

/**
 * 单个日志模板的聚合摘要。
 *
 * @param clusterId              Drain3 簇 id
 * @param template               模板文本（变量已用掩码替换）
 * @param count                  命中该模板的日志条数
 * @param firstSeen              首条日志时间（ISO-8601，可为空）
 * @param lastSeen               末条日志时间（ISO-8601，可为空）
 * @param severityDistribution   level -> 条数
 * @param representativeSamples  代表样本（已脱敏，有界）
 * @param parameterDistribution  参数掩码 -> 脱敏取值列表
 * @param truncated              是否因上限被截断
 */
public record LogTemplateSummary(int clusterId, String template, int count, String firstSeen, String lastSeen,
                                 Map<String, Integer> severityDistribution, List<String> representativeSamples,
                                 ParameterDistribution parameterDistribution, boolean truncated) {
}
