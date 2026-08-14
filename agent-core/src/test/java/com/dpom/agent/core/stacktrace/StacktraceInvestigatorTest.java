package com.dpom.agent.core.stacktrace;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.common.codegraph.Symbol;
import com.dpom.agent.core.workspace.CodeWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 堆栈调查器验收测试：解析快照、读源码、代码图导航、根因引用源码位置。
 */
class StacktraceInvestigatorTest {

    /**
     * 跑通 Controller→Service→Repository 明确异常的堆栈调查。
     */
    @Test
    void investigatesStacktrace(@TempDir Path tempDir) throws Exception {
        // Spring fixture：AssetController → AssetService → AssetRepository，第 42 行抛异常。
        Files.writeString(tempDir.resolve("AssetController.java"), "controller\\n");
        Files.writeString(tempDir.resolve("AssetService.java"), "service\\n");
        List<String> repositoryLines = new ArrayList<>();
        for (int i = 1; i <= 41; i++) {
            repositoryLines.add("    // 占位行 " + i);
        }
        repositoryLines.add("    throw new IllegalStateException(\"设备落库失败\");");
        Files.write(tempDir.resolve("AssetRepository.java"), repositoryLines);

        CodeGraphClient codeGraphClient = mock(CodeGraphClient.class);
        when(codeGraphClient.resolveSnapshot("asset-service", "abc123"))
                .thenReturn(new CodeSnapshot("s1", "asset-service", "abc123", tempDir.toString(), SnapshotStatus.READY));
        when(codeGraphClient.findCallers("s1", "com.example.asset.AssetRepository.insert"))
                .thenReturn(List.of(new Symbol("com.example.asset.AssetService.create", "method", "AssetService.java", 35)));

        String stacktrace = """
                java.lang.IllegalStateException: 设备落库失败
                    at com.example.asset.AssetRepository.insert(AssetRepository.java:42)
                    at com.example.asset.AssetService.create(AssetService.java:35)
                    at com.example.asset.AssetController.create(AssetController.java:20)
                    at java.lang.reflect.Method.invoke(Method.java:566)
                    at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:205)
                """;

        StacktraceInvestigator investigator =
                new StacktraceInvestigator(codeGraphClient, new CodeWorkspace(), new StacktraceParser());
        StacktraceReport report = investigator.investigate("asset-service", "abc123", stacktrace);

        assertThat(report.snapshot().commitSha()).isEqualTo("abc123");

        assertThat(report.sourceEvidence()).hasSize(1);
        SourceEvidence source = report.sourceEvidence().get(0);
        assertThat(source.filePath()).isEqualTo("AssetRepository.java");
        assertThat(source.lineNumber()).isEqualTo(42);
        assertThat(source.lineContent()).contains("IllegalStateException");

        assertThat(report.graphEvidence()).hasSize(1);
        assertThat(report.graphEvidence().get(0).queryType()).isEqualTo("findCallers");

        // 根因必须引用源码位置，而非仅引用代码图文本。
        assertThat(report.rootCause()).contains("AssetRepository.java:42");
    }
}