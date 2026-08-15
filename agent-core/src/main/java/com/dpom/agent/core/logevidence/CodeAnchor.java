package com.dpom.agent.core.logevidence;

/**
 * 从日志证据中确定性提取的代码锚点。
 *
 * @param type            类型：EXCEPTION / STACK_FRAME / CLASS_METHOD / HTTP_PATH / MAPPER_ID / LOG_CONSTANT
 * @param value           锚点值（如类全限定名、调用点、路径）
 * @param sourceEvidenceId 来源日志证据 id
 * @param confidence      置信度（0.0~1.0）
 * @param ruleVersion     提取规则版本
 */
public record CodeAnchor(String type, String value, String sourceEvidenceId, double confidence, String ruleVersion) {
}
