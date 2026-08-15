package com.dpom.agent.core.logevidence;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T102 有界摄入、前缀分离与脱敏验收。
 */
class LogIntakeRedactionTest {

    private final BoundedLogIntake intake = new BoundedLogIntake();
    private final LogPrefixSplitter splitter = new LogPrefixSplitter();
    private final LogRedactor redactor = new LogRedactor();

    /**
     * 行数与总字节上限生效，并记录截断原因。
     */
    @Test
    void boundedIntakeTruncatesAndRecordsReason() {
        List<String> lines = List.of("a", "b", "c", "d", "e");
        LogIntakeResult result = intake.intake(lines, new LogIntakeLimits(3, 1000, 100, 10, 5, 5));

        assertThat(result.originalCount()).isEqualTo(5);
        assertThat(result.retainedCount()).isEqualTo(3);
        assertThat(result.lines()).containsExactly("a", "b", "c");
        assertThat(result.truncationReasons()).contains("MAX_LINES");
    }

    /**
     * 单行超长时截断该行并记录原因。
     */
    @Test
    void boundedIntakeTruncatesOversizedLine() {
        List<String> lines = List.of("short", "x".repeat(50));
        LogIntakeResult result = intake.intake(lines, new LogIntakeLimits(10, 1000, 10, 10, 5, 5));

        assertThat(result.retainedCount()).isEqualTo(2);
        assertThat(result.lines().get(1)).hasSize(10);
        assertThat(result.truncationReasons()).contains("MAX_LINE_BYTES");
    }

    /**
     * 结构化前缀分离：timestamp/level/logger 与 message 分离。
     */
    @Test
    void prefixSplitSeparatesStructuredFields() {
        StructuredLog withLogger = splitter.split("ERROR com.example.AssetService - device insert failed");
        assertThat(withLogger.level()).isEqualTo("ERROR");
        assertThat(withLogger.logger()).isEqualTo("com.example.AssetService");
        assertThat(withLogger.message()).isEqualTo("device insert failed");

        StructuredLog plain = splitter.split("INFO some message");
        assertThat(plain.level()).isEqualTo("INFO");
        assertThat(plain.logger()).isEmpty();
        assertThat(plain.message()).isEqualTo("some message");

        StructuredLog withTs = splitter.split("[2026-08-14 10:00:00] ERROR com.example.Svc - boom");
        assertThat(withTs.timestamp()).isEqualTo("[2026-08-14 10:00:00]");
        assertThat(withTs.logger()).isEqualTo("com.example.Svc");
        assertThat(withTs.message()).isEqualTo("boom");
    }

    /**
     * 无法识别级别时整行作为 message。
     */
    @Test
    void prefixSplitFallsBackToWholeMessage() {
        StructuredLog s = splitter.split("2026-08-14 plain unstructured line");
        assertThat(s.level()).isEqualTo("INFO");
        assertThat(s.message()).isEqualTo("2026-08-14 plain unstructured line");
    }

    /**
     * 敏感键值脱敏为稳定 hash，原始值不得出现。
     */
    @Test
    void redactsSensitiveValues() {
        String out = redactor.redact("password=secret123 token=abc Authorization: Bearer xyz");
        assertThat(out).doesNotContain("secret123").doesNotContain("abc").doesNotContain("xyz");
        assertThat(out).contains("h:");

        assertThat(redactor.redact("user alice@example.com from 10.0.0.1"))
                .contains("[REDACTED:email]").contains("[REDACTED:ip]");
    }

    /**
     * 稳定 hash 对同值一致、对不同值不同。
     */
    @Test
    void stableHashIsDeterministicAndDistinct() {
        assertThat(redactor.stableHash("same")).isEqualTo(redactor.stableHash("same"));
        assertThat(redactor.stableHash("same")).isNotEqualTo(redactor.stableHash("other"));
        assertThat(redactor.stableHash("same")).startsWith("h:");
    }

    /**
     * UTF-8 单行字节截断不破坏多字节字符。
     */
    @Test
    void utf8TruncationDoesNotSplitMultibyte() {
        String line = "中文😀"; // 6 + 4 = 10 UTF-8 字节
        LogIntakeResult result = intake.intake(List.of(line), new LogIntakeLimits(10, 1000, 8, 100, 5, 5));

        assertThat(result.lines().get(0)).isEqualTo("中文");
        assertThat(result.lines().get(0).getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(8);
        assertThat(result.truncationReasons()).contains("MAX_LINE_BYTES");
    }

    /**
     * UTF-8 总字节预算对中英文混合日志生效。
     */
    @Test
    void utf8TotalBytesLimit() {
        List<String> lines = List.of("中文内容A", "中文内容B", "中文内容C");
        LogIntakeResult result = intake.intake(lines, new LogIntakeLimits(10, 30, 100, 100, 5, 5));

        int total = result.lines().stream().mapToInt(l -> l.getBytes(StandardCharsets.UTF_8).length).sum();
        assertThat(total).isLessThanOrEqualTo(30);
        assertThat(result.truncationReasons()).contains("MAX_TOTAL_BYTES");
    }
}
