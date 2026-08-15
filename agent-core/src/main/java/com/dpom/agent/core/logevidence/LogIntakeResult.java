package com.dpom.agent.core.logevidence;

import java.util.List;

/**
 * 有界摄入结果：保留的日志行与截断元数据。
 *
 * @param lines             保留的日志行（已截断）
 * @param originalCount     原始行数
 * @param retainedCount     保留行数
 * @param truncationReasons 截断原因列表（如 MAX_LINES/MAX_LINE_BYTES/MAX_TOTAL_BYTES）
 */
public record LogIntakeResult(List<String> lines, int originalCount, int retainedCount,
                              List<String> truncationReasons) {
}
