package com.dpom.agent.core.eval;

import java.util.List;

/**
 * 一个评测案例：incident 身份、原始日志与期望。
 *
 * @param id          案例 id
 * @param serviceCode 服务编码
 * @param environment 环境
 * @param release     发布版本
 * @param commit      提交 SHA
 * @param symptom     症状
 * @param logs        原始日志行
 * @param expected    期望断言
 */
public record EvalCase(String id, String serviceCode, String environment, String release, String commit,
                       String symptom, List<String> logs, EvalExpected expected) {
}
