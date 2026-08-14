package com.dpom.agent.common.logtemplate;

/**
 * 已学习的日志模板。
 *
 * @param clusterId 簇 id
 * @param size      簇大小（消息数）
 * @param template  模板文本
 */
public record LogTemplate(int clusterId, int size, String template) {
}
