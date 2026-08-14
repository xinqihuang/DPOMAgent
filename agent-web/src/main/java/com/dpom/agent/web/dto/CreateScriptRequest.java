package com.dpom.agent.web.dto;

/**
 * 创建脚本工件请求。
 *
 * @param type                  脚本类型（READ_ONLY_DIAGNOSTIC / MITIGATION）
 * @param language              语言
 * @param purpose               用途
 * @param riskLevel             风险等级
 * @param preconditions         前置条件
 * @param verification          验证方式
 * @param rollback              回滚方案
 * @param content               脚本内容
 * @param hypothesesToValidate  待验证假设
 * @param expectedOutput        期望输出
 * @param instructions          执行说明
 */
public record CreateScriptRequest(String type, String language, String purpose, String riskLevel,
                                  String preconditions, String verification, String rollback, String content,
                                  String hypothesesToValidate, String expectedOutput, String instructions) {
}
