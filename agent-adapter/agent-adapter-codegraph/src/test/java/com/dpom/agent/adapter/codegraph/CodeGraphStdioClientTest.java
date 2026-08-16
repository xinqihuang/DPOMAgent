package com.dpom.agent.adapter.codegraph;

import com.dpom.agent.common.codegraph.CodeGraphQueryException;
import com.dpom.agent.common.codegraph.CodeGraphTimeoutException;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.RegisteredRepository;
import com.dpom.agent.common.codegraph.RepositoryRegistry;
import com.dpom.agent.common.codegraph.SnapshotNotFoundException;
import com.dpom.agent.common.codegraph.Symbol;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CodeGraph stdio 客户端测试：resolveSnapshot 走仓库注册表，查询前把 snapshotId 反查校验为受控 projectPath，
 * 禁止任意路径直接进入 MCP 参数；工具调用映射到 CodeGraph MCP 工具。
 */
class CodeGraphStdioClientTest {

    private static final String PROJECT = "/snapshots/asset-service";
    private static final String PROJECT_PATH = Path.of(PROJECT).toString();

    private McpSyncClient mcpClient;
    private CodeGraphStdioClient client;

    @BeforeEach
    void setUp() {
        mcpClient = mock(McpSyncClient.class);
        RepositoryRegistry registry = new RepositoryRegistry() {
            @Override
            public RegisteredRepository resolve(String serviceCode, String commitSha) {
                return new RegisteredRepository(serviceCode, "1.0.0", commitSha, Path.of(PROJECT));
            }

            @Override
            public RegisteredRepository resolveByProjectPath(String projectPath) {
                if (!Path.of(projectPath).equals(Path.of(PROJECT))) {
                    throw new SnapshotNotFoundException("未注册的 projectPath：" + projectPath);
                }
                return new RegisteredRepository("asset-service", "1.0.0", "abc123", Path.of(PROJECT));
            }
        };
        client = new CodeGraphStdioClient(() -> mcpClient, registry);
    }

    @Test
    void resolvesSnapshotViaRegistry() {
        CodeSnapshot snapshot = client.resolveSnapshot("asset-service", "abc123");

        assertThat(snapshot.snapshotId()).isEqualTo(PROJECT_PATH);
        assertThat(snapshot.serviceCode()).isEqualTo("asset-service");
        assertThat(snapshot.commitSha()).isEqualTo("abc123");
        assertThat(snapshot.workspacePath()).isEqualTo(PROJECT_PATH);
        verify(mcpClient, org.mockito.Mockito.never()).callTool(any());
    }

    @Test
    void getSnapshotResolvesByProjectPath() {
        CodeSnapshot snapshot = client.getSnapshot(PROJECT_PATH);

        assertThat(snapshot.snapshotId()).isEqualTo(PROJECT_PATH);
        assertThat(snapshot.serviceCode()).isEqualTo("asset-service");
        assertThat(snapshot.commitSha()).isEqualTo("abc123");
    }

    @Test
    void getSnapshotRejectsUnregisteredPath() {
        assertThatThrownBy(() -> client.getSnapshot("C:\\other\\repo"))
                .isInstanceOf(SnapshotNotFoundException.class);
    }

    @Test
    void findsSymbolViaSearch() {
        when(mcpClient.callTool(new CallToolRequest("codegraph_search",
                Map.of("query", "create", "projectPath", PROJECT_PATH))))
                .thenReturn(result("**Search Results (1 found)**\n\n**create** (method)\nAssetService.java:8\n"));

        List<Symbol> symbols = client.findSymbol(PROJECT_PATH, "create");

        assertThat(symbols).hasSize(1);
        assertThat(symbols.get(0).name()).isEqualTo("create");
    }

    @Test
    void findsCallersViaCallersTool() {
        when(mcpClient.callTool(new CallToolRequest("codegraph_callers",
                Map.of("symbol", "insert", "projectPath", PROJECT_PATH))))
                .thenReturn(result("**Callers of insert (1 found)**\n\n- create (method) - AssetService.java:8\n"));

        List<Symbol> callers = client.findCallers(PROJECT_PATH, "insert");

        assertThat(callers).hasSize(1);
        assertThat(callers.get(0).name()).isEqualTo("create");
    }

    @Test
    void findsCalleesViaCalleesTool() {
        when(mcpClient.callTool(new CallToolRequest("codegraph_callees",
                Map.of("symbol", "create", "projectPath", PROJECT_PATH))))
                .thenReturn(result("**Callees of create (1 found)**\n\n- insert (method) - AssetRepository.java:6\n"));

        List<Symbol> callees = client.findCallees(PROJECT_PATH, "create");

        assertThat(callees).hasSize(1);
        assertThat(callees.get(0).name()).isEqualTo("insert");
    }

    @Test
    void findsImpactViaImpactTool() {
        when(mcpClient.callTool(new CallToolRequest("codegraph_impact",
                Map.of("symbol", "insert", "projectPath", PROJECT_PATH))))
                .thenReturn(result("**Impact: \"insert\" affects 1 symbols**\n\n**AssetService.java:**\ncreate:8\n"));

        List<Symbol> impact = client.findImpact(PROJECT_PATH, "insert");

        assertThat(impact).hasSize(1);
        assertThat(impact.get(0).name()).isEqualTo("create");
    }

    @Test
    void findsCallChainViaExplore() {
        when(mcpClient.callTool(new CallToolRequest("codegraph_explore",
                Map.of("query", "A B", "projectPath", PROJECT_PATH))))
                .thenReturn(result("Found 2 symbols.\n- A.a (method) - A.java:1\n- B.b (method) - B.java:2\n"));

        var chain = client.findCallChain(PROJECT_PATH, "A", "B");

        assertThat(chain).hasSize(2);
    }

    @Test
    void findsClassHierarchyViaNode() {
        when(mcpClient.callTool(new CallToolRequest("codegraph_node",
                Map.of("symbol", "AssetServiceImpl", "includeCode", false, "projectPath", PROJECT_PATH))))
                .thenReturn(result("**AssetServiceImpl** (class)\n\n**Signature:** `class AssetServiceImpl extends BaseService`\n"));

        var hierarchy = client.findClassHierarchy(PROJECT_PATH, "AssetServiceImpl");

        assertThat(hierarchy.ancestors()).containsExactly("BaseService");
    }

    @Test
    void rejectsArbitraryAbsolutePathAsProjectPath() {
        assertThatThrownBy(() -> client.findSymbol("C:\\arbitrary\\repo", "x"))
                .isInstanceOf(SnapshotNotFoundException.class);
    }

    @Test
    void rejectsPathTraversalAsProjectPath() {
        assertThatThrownBy(() -> client.findCallers("..", "x"))
                .isInstanceOf(SnapshotNotFoundException.class);
    }

    @Test
    void rejectsUnregisteredSnapshotId() {
        assertThatThrownBy(() -> client.findCallees("/not/a/registered/root", "x"))
                .isInstanceOf(SnapshotNotFoundException.class);
    }

    @Test
    void mapsErrorResult() {
        when(mcpClient.callTool(any(CallToolRequest.class)))
                .thenReturn(new CallToolResult(List.<Content>of(new TextContent("boom")), true));

        assertThatThrownBy(() -> client.findCallers(PROJECT_PATH, "A"))
                .isInstanceOf(CodeGraphQueryException.class);
    }

    @Test
    void mapsTimeout() {
        when(mcpClient.callTool(any(CallToolRequest.class)))
                .thenThrow(new RuntimeException("Request timed out after 30s"));

        assertThatThrownBy(() -> client.findCallers(PROJECT_PATH, "A"))
                .isInstanceOf(CodeGraphTimeoutException.class);
    }

    @Test
    void mapsTransportClosed() {
        when(mcpClient.callTool(any(CallToolRequest.class)))
                .thenThrow(new RuntimeException("Transport closed"));

        assertThatThrownBy(() -> client.findCallers(PROJECT_PATH, "A"))
                .isInstanceOf(CodeGraphQueryException.class);
    }

    private CallToolResult result(String text) {
        return new CallToolResult(List.<Content>of(new TextContent(text)), false);
    }
}
