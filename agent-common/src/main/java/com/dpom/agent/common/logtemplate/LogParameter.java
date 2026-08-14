package com.dpom.agent.common.logtemplate;

/**
 * 日志模板抽取出的一个参数。
 *
 * @param value 参数值
 * @param mask  掩码名（如 IP、NUM、*）
 */
public record LogParameter(String value, String mask) {
}
