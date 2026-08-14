package com.dpom.agent.core.workspace;

/**
 * 一次文本搜索命中。
 *
 * @param filePath   相对根目录的文件路径
 * @param lineNumber 行号（从 1 开始）
 * @param line       命中的行内容
 */
public record SearchHit(String filePath, int lineNumber, String line) {
}
