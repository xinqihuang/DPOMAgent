package com.dpom.agent.web.dto;

import java.util.List;

/**
 * 调查提交请求。
 *
 * @param serviceCode    服务编码
 * @param environment    环境
 * @param release        发布版本
 * @param commit         提交 SHA
 * @param symptom        症状
 * @param timeRange      时间范围
 * @param logs           日志行
 * @param idempotencyKey 幂等键
 */
public record InvestigationSubmitRequest(String serviceCode, String environment, String release, String commit,
                                         String symptom, String timeRange, List<String> logs,
                                         String idempotencyKey) {
}
