package com.dpom.agent.core.logevidence;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 有界日志摄入：按行数、单行 UTF-8 字节、总 UTF-8 字节确定性截断，并记录截断原因，绝不静默丢弃。
 */
public class BoundedLogIntake {

    /**
     * 摄入日志并执行有界截断。
     *
     * @param rawLines 原始日志行
     * @param limits   摄入上限
     * @return 有界摄入结果
     */
    public LogIntakeResult intake(List<String> rawLines, LogIntakeLimits limits) {
        List<String> retained = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        int totalBytes = 0;
        int originalCount = rawLines == null ? 0 : rawLines.size();
        for (String raw : rawLines) {
            if (retained.size() >= limits.maxLines()) {
                reasons.add("MAX_LINES");
                break;
            }
            String line = truncateUtf8(raw, limits.maxLineBytes());
            if (line.length() != raw.length()) {
                reasons.add("MAX_LINE_BYTES");
            }
            int lineBytes = line.getBytes(StandardCharsets.UTF_8).length;
            if (totalBytes + lineBytes > limits.maxTotalBytes()) {
                reasons.add("MAX_TOTAL_BYTES");
                break;
            }
            retained.add(line);
            totalBytes += lineBytes;
        }
        return new LogIntakeResult(retained, originalCount, retained.size(), reasons);
    }

    /**
     * 按 UTF-8 字节截断，退回多字节字符边界，避免破坏字符。
     */
    private String truncateUtf8(String s, int maxBytes) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return s;
        }
        int end = maxBytes;
        while (end > 0 && (bytes[end] & 0xC0) == 0x80) {
            end--;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
    }
}
