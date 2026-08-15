package com.dpom.agent.core.tool;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.Symbol;
import com.dpom.agent.common.llm.ToolDefinition;
import com.dpom.agent.common.runtime.ArtifactRef;
import com.dpom.agent.common.runtime.ObservationInput;
import com.dpom.agent.common.runtime.RuntimeEvidenceClient;
import com.dpom.agent.core.investigation.ToolAction;
import com.dpom.agent.core.investigation.ToolExecutionResult;
import com.dpom.agent.core.workspace.CodeWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 工具集与工具执行器验收测试：13 个工具、无 execute_shell、代码/运行时/工作区分发。
 */
class InvestigationToolExecutorTest {

    /**
     * 工具集包含 13 个工具且不包含 execute_shell。
     */
    @Test
    void toolsetHasThirteenToolsAndNoShell() {
        List<ToolDefinition> definitions = Toolset.definitions();

        assertThat(definitions).hasSize(13);
        assertThat(definitions).extracting(ToolDefinition::name).doesNotContain("execute_shell");
        assertThat(definitions).extracting(ToolDefinition::name).contains(
                "list_files", "search_text", "read_source", "find_symbol", "find_callers", "find_callees",
                "find_call_chain", "find_class_hierarchy", "search_logs", "query_trace", "query_alerts",
                "query_metrics", "mine_log_templates");
    }

    /**
     * mine_log_templates 的 lines 必须是 string array，而非 string。
     */
    @Test
    void mineLogTemplatesLinesIsStringArray() {
        ToolDefinition tool = Toolset.definitions().stream()
                .filter(t -> t.name().equals("mine_log_templates"))
                .findFirst()
                .orElseThrow();

        assertThat(tool.parametersJson())
                .contains("\"lines\"")
                .contains("\"array\"")
                .contains("\"items\"")
                .contains("\"string\"");
    }

    /**
     * 工具执行器能分发到代码图/运行时/工作区。
     */
    @Test
    void executesCodeRuntimeAndWorkspaceTools(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("A.java"), "insert into asset");

        CodeGraphClient codeGraphClient = mock(CodeGraphClient.class);
        when(codeGraphClient.findCallers("s1", "AssetRepository.insert"))
                .thenReturn(List.of(new Symbol("AssetService.create", "method", "AssetService.java", 35)));
        RuntimeEvidenceClient runtimeClient = mock(RuntimeEvidenceClient.class);
        when(runtimeClient.searchLogs("asset", "prod", "INSERT", "1h"))
                .thenReturn(List.of(new ObservationInput(new ArtifactRef("logs", "l1", "asset"), "INSERT 失败", "{}")));

        InvestigationToolExecutor executor = new InvestigationToolExecutor(
                "s1", tempDir, "asset", "prod", codeGraphClient, new CodeWorkspace(), runtimeClient);

        ToolExecutionResult codeResult = executor.execute(
                new ToolAction("find_callers", "{\"symbol\":\"AssetRepository.insert\"}", "查调用方"));
        assertThat(codeResult.source()).isEqualTo("codegraph");

        ToolExecutionResult runtimeResult = executor.execute(
                new ToolAction("search_logs", "{\"keyword\":\"INSERT\"}", "查日志"));
        assertThat(runtimeResult.source()).isEqualTo("runtime");

        ToolExecutionResult workspaceResult = executor.execute(
                new ToolAction("read_source", "{\"path\":\"A.java\"}", "读源码"));
        assertThat(workspaceResult.source()).isEqualTo("workspace");
        assertThat(workspaceResult.summary()).contains("insert into asset");

        ToolExecutionResult unknown = executor.execute(new ToolAction("execute_shell", "{}", "shell"));
        assertThat(unknown.source()).isEqualTo("error");
    }
}
