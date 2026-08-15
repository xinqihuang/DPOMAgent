package com.dpom.agent.core.eval;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 回归结果写出器：运行前删除旧结果，用临时文件 + atomic move 写出，避免旧结果冒充新结果。
 */
public class BenchmarkResultWriter {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 写出结果。
     *
     * @param results 案例结果
     * @param metrics 指标
     * @param out     目标文件
     * @param model   模型名
     * @param promptVersion prompt 版本
     * @param toolsetVersion toolset 版本
     * @param ruleVersion 规则版本
     * @param minerVersion 挖掘器版本
     * @throws IOException 写出失败
     */
    public void write(List<BenchmarkCaseResult> results, BenchmarkMetrics metrics, Path out, String model,
                      String promptVersion, String toolsetVersion, String ruleVersion, String minerVersion)
            throws IOException {
        Files.deleteIfExists(out);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("executed", true);
        root.put("overallPassed", metrics.overallPassed());
        root.put("timestamp", Instant.now().toString());
        root.put("model", model);
        root.put("promptVersion", promptVersion);
        root.put("toolsetVersion", toolsetVersion);
        root.put("ruleVersion", ruleVersion);
        root.put("minerVersion", minerVersion);
        root.put("metrics", metrics);
        root.put("cases", results);
        Files.createDirectories(out.getParent());
        Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), root);
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
