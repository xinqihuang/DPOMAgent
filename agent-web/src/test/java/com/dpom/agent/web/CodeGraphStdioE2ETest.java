package com.dpom.agent.web;

import com.dpom.agent.adapter.codegraph.CodeGraphVersionValidator;
import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.Symbol;
import com.dpom.agent.web.support.CodeGraphTestSupport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 CodeGraph stdio E2E：由 DPOM_CODEGRAPH_E2E=true 显式启用，默认跳过；
 * 本机未安装固定版本时报告 NOT_EXECUTED，不伪造通过。
 */
@EnabledIfEnvironmentVariable(named = "DPOM_CODEGRAPH_E2E", matches = "true")
class CodeGraphStdioE2ETest {

    @Test
    void runsRealCodeGraphQueryAgainstFixture() {
        String executable = System.getenv().getOrDefault("DPOM_CODEGRAPH_EXECUTABLE", "codegraph");
        String expectedVersion = System.getenv().getOrDefault("DPOM_CODEGRAPH_VERSION", "1.5.0");
        Path executablePath = Path.of(executable);

        CodeGraphVersionValidator validator = new CodeGraphVersionValidator();
        try {
            validator.validate(executablePath, expectedVersion);
        } catch (RuntimeException e) {
            Assumptions.abort("NOT_EXECUTED: CodeGraph 未安装或版本不匹配（" + e.getMessage() + "）");
            return;
        }

        Path fixture = locateFixture();

        CodeGraphClient client = CodeGraphTestSupport.stdioClient(executable, Map.of(
                "asset-service", fixture.resolve("asset-service"),
                "telemetry-service", fixture.resolve("telemetry-service"),
                "gateway-service", fixture.resolve("gateway-service")));

        // 必须先 resolveSnapshot，再用返回的受控 snapshotId（projectPath 经 Registry 反查校验，禁止任意路径）
        var snapshot = client.resolveSnapshot("asset-service", CodeGraphTestSupport.SNAPSHOT_COMMIT);
        List<Symbol> symbols = client.findSymbol(snapshot.snapshotId(), "AssetService");
        assertThat(symbols).as("CodeGraph 索引后应能搜索到 AssetService").isNotEmpty();
    }

    /**
     * Locates the fixture from any Maven module working directory. Once the real E2E is enabled,
     * a missing fixture is a test configuration error rather than an optional skip.
     */
    private static Path locateFixture() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path fixture = current.resolve("test-fixtures").resolve("energy-platform-demo");
            if (Files.isDirectory(fixture)) {
                return fixture;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("energy-platform-demo fixture not found from "
                + System.getProperty("user.dir"));
    }
}
