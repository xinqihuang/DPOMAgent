package com.dpom.agent.core.script;

import java.time.LocalDateTime;

/**
 * 脚本工件：交给 SRE 执行的诊断或修复脚本（仅生成，不执行）。
 *
 * @param id                    主键
 * @param investigationId       关联调查 id
 * @param type                  脚本类型（对应列 script_type）
 * @param language              语言（shell/python/sql）
 * @param purpose               用途
 * @param riskLevel             风险等级
 * @param readOnly              是否只读
 * @param approvalStatus        审批状态
 * @param preconditions         前置条件
 * @param verification          验证方式
 * @param rollback              回滚方案
 * @param content               脚本内容
 * @param hypothesesToValidate  待验证假设（诊断脚本用，可为空）
 * @param expectedOutput        期望输出（诊断脚本用，可为空）
 * @param instructions          执行说明（可为空）
 * @param rootCause             根因（修复脚本用，可为空）
 * @param evidenceIds           证据 id（修复脚本用，可为空）
 * @param target                修复目标（修复脚本用，可为空）
 * @param createdAt             创建时间
 */
public record ScriptArtifact(Long id, Long investigationId, String type, String language, String purpose,
                             String riskLevel, boolean readOnly, String approvalStatus,
                             String preconditions, String verification, String rollback, String content,
                             String hypothesesToValidate, String expectedOutput, String instructions,
                             String rootCause, String evidenceIds, String target,
                             LocalDateTime createdAt) {
}
