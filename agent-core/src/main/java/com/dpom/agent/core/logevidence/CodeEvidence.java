package com.dpom.agent.core.logevidence;

/**
 * 绑定 commit 的代码证据：锚点经代码图导航后读取同一快照的事实源码。
 *
 * @param evidenceId  证据 id
 * @param anchorValue 锚点值
 * @param symbol      符号名
 * @param filePath    文件路径
 * @param lineNumber  行号（可为空）
 * @param commit      提交 SHA
 * @param excerpt     源码片段（已验证）
 * @param status      VERIFIED / WORKSPACE_FALLBACK / NOT_READY / VERSION_MISMATCH
 */
public record CodeEvidence(String evidenceId, String anchorValue, String symbol, String filePath, Integer lineNumber,
                           String commit, String excerpt, String status) {
}
