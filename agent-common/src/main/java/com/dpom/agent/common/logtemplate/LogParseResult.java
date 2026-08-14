package com.dpom.agent.common.logtemplate;

import java.util.List;

/**
 * 一条日志的模板挖掘结果。
 *
 * @param clusterId   簇 id
 * @param clusterSize 簇大小
 * @param template    挖掘出的模板
 * @param params      抽取的参数
 */
public record LogParseResult(int clusterId, int clusterSize, String template, List<LogParameter> params) {
}
