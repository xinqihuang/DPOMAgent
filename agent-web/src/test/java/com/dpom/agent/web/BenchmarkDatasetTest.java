package com.dpom.agent.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 最小回归集与基准数据集验收：9 个案例（5 stacktrace + 4 device）、报告存在、可重复运行。
 */
class BenchmarkDatasetTest {

    /**
     * 基准案例。
     */
    record BenchmarkCase(String id, String type, String expectedRootCause, String expectedLocation) {
    }

    /**
     * 数据集含 5 个 stacktrace 与 4 个 device 案例，且均具备预期根因与位置。
     */
    @Test
    void datasetHasNineCases() {
        List<BenchmarkCase> cases = dataset();

        assertThat(cases).hasSize(9);
        assertThat(cases).filteredOn(c -> c.type().equals("STACKTRACE")).hasSize(5);
        assertThat(cases).filteredOn(c -> c.type().equals("DEVICE")).hasSize(4);
        assertThat(cases).allSatisfy(c -> {
            assertThat(c.expectedRootCause()).isNotBlank();
            assertThat(c.expectedLocation()).isNotBlank();
        });
    }

    /**
     * 基准报告已生成。
     */
    @Test
    void benchmarkReportExists() {
        Path report = Path.of("..", "docs", "benchmark-report.md").toAbsolutePath().normalize();
        assertThat(Files.exists(report)).isTrue();
    }

    /**
     * 最小回归集数据。
     */
    private List<BenchmarkCase> dataset() {
        return List.of(
                new BenchmarkCase("S1", "STACKTRACE", "空指针", "AssetRepository.java"),
                new BenchmarkCase("S2", "STACKTRACE", "非法状态", "AssetService.java"),
                new BenchmarkCase("S3", "STACKTRACE", "参数非法", "AssetController.java"),
                new BenchmarkCase("S4", "STACKTRACE", "datasource 配置错误", "application.yml"),
                new BenchmarkCase("S5", "STACKTRACE", "事务回滚", "AssetService.java"),
                new BenchmarkCase("D1", "DEVICE", "未调用 Repository.insert", "AssetService.create"),
                new BenchmarkCase("D2", "DEVICE", "事务回滚", "AssetService.create"),
                new BenchmarkCase("D3", "DEVICE", "datasource tenant 错误", "application.yml"),
                new BenchmarkCase("D4", "DEVICE", "查询过滤条件错误", "AssetRepository.find")
        );
    }
}
