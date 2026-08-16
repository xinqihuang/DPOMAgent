package com.dpom.agent.core.persistence;

/**
 * 升级判定原始行（持久化原始串，反序列化在领域侧完成）。
 *
 * @param eligible        是否升级
 * @param reasons         逗号分隔的升级原因名
 * @param missingEvidence 缺失证据 JSON
 * @param confidence      置信度
 */
public record EscalationRow(boolean eligible, String reasons, String missingEvidence, int confidence) {
}
