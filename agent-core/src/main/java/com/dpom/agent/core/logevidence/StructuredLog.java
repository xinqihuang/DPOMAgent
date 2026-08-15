package com.dpom.agent.core.logevidence;

/**
 * 结构化日志：把 timestamp/level/logger 等结构化字段与 message 分离。
 *
 * @param timestamp 时间戳（可为空串）
 * @param level     级别
 * @param logger    logger/类名（可为空串）
 * @param message   非结构化消息体（送入 Drain3 的部分）
 */
public record StructuredLog(String timestamp, String level, String logger, String message) {
}
