package com.dpom.agent.core.logevidence;

/**
 * 证据来源与版本元数据：记录证据由哪个阶段产出、绑定哪个 commit 与规则版本。
 *
 * @param source      来源：drain3 / codegraph / workspace / redaction
 * @param commit      绑定提交 SHA（可为空）
 * @param filePath    代码文件路径（源码证据，可为空）
 * @param lineNumber  代码行号（源码证据，可为空）
 * @param ruleVersion 提取规则版本（锚点证据）
 * @param extractedAt 提取时间（ISO-8601）
 */
public record EvidenceProvenance(String source, String commit, String filePath, Integer lineNumber,
                                 String ruleVersion, String extractedAt) {
}
