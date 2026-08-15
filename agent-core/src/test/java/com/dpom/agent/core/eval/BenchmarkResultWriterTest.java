package com.dpom.agent.core.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回归结果写出验收：旧结果不被误用。
 */
class BenchmarkResultWriterTest {

    @Test
    void staleResultIsDeletedAndReplaced(@TempDir Path tempDir) throws Exception {
        Path out = tempDir.resolve("diagnostic-regression.json");
        Files.writeString(out, "{\"overallPassed\":true}");

        BenchmarkCaseResult r = new BenchmarkCaseResult("E01", true, false, "FAILED", null, null, null,
                List.of(), List.of(), List.of(), 0, 10, List.of("X"));
        BenchmarkMetrics m = BenchmarkMetrics.compute(List.of(r));
        new BenchmarkResultWriter().write(List.of(r), m, out, "m", "v1", "v1", "v1", "v1");

        String content = Files.readString(out);
        assertThat(content).contains("\"overallPassed\" : false");
        assertThat(content).doesNotContain("\"overallPassed\":true");
        assertThat(Files.exists(out.resolveSibling("diagnostic-regression.json.tmp"))).isFalse();
    }
}
