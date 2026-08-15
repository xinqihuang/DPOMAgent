package com.dpom.agent.web.dto;

import java.time.LocalDateTime;

/**
 * 时间线步骤响应。
 *
 * @param order     顺序
 * @param type      类型
 * @param summary   摘要
 * @param createdAt 创建时间
 */
public record StepResponse(int order, String type, String summary, LocalDateTime createdAt) {
}
