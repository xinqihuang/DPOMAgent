package com.dpom.agent.common.codegraph;

/**
 * 调用链中的一步。
 *
 * @param symbol     符号名
 * @param filePath   文件路径
 * @param lineNumber 行号（可为空）
 */
public record CallStep(String symbol, String filePath, Integer lineNumber) {
}
