package com.dpom.agent.common.codegraph;

/**
 * 代码符号（类/方法等）。
 *
 * @param name       符号名
 * @param kind       符号类型（class/method 等）
 * @param filePath   文件路径
 * @param lineNumber 行号（可为空）
 */
public record Symbol(String name, String kind, String filePath, Integer lineNumber) {
}
