package com.dpom.agent.core.stacktrace;

/**
 * 源码证据：来自快照工作区真实源码，而非代码图文本。
 *
 * @param filePath    文件相对路径
 * @param lineNumber  行号
 * @param lineContent 行内容
 */
public record SourceEvidence(String filePath, Integer lineNumber, String lineContent) {
}
