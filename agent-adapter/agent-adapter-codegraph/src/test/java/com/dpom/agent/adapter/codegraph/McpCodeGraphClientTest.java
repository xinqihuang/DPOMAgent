package com.dpom.agent.adapter.codegraph;

import com.dpom.agent.common.codegraph.CallStep;
import com.dpom.agent.common.codegraph.ClassHierarchy;
import com.dpom.agent.common.codegraph.CodeGraphQueryException;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.common.codegraph.Symbol;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MCP 代码图客户端验收：快照解析、调用方、层次、错误映射。
 */
class McpCodeGraphClientTest {

    private McpSyncClient mcpClient;

    private McpCodeGraphClient client;

    @BeforeEach
    void setUp() {
        mcpClient = mock(McpSyncClient.class);
        client = new McpCodeGraphClient(() -> mcpClient);
    }

    /**
     * 解析快照（映射到索引仓库）。
     */
    @Test
    void resolvesSnapshot() {
        when(mcpClient.callTool(new CallToolRequest("list_indexed_repositories", Map.of())))
                .thenReturn(result("{\"success\":true,\"repositories\":[\"/repos/asset-service\"]}"));

        CodeSnapshot snapshot = client.resolveSnapshot("asset-service", "abc123");

        assertThat(snapshot.workspacePath()).isEqualTo("/repos/asset-service");
        assertThat(snapshot.commitSha()).isEqualTo("abc123");
        assertThat(snapshot.status()).isEqualTo(SnapshotStatus.READY);
    }

    /**
     * 查找调用方。
     */
    @Test
    void findsCallers() {
        when(mcpClient.callTool(new CallToolRequest("analyze_code_relationships",
                Map.of("query_type", "find_callers", "target", "A.insert", "repo_path", "r"))))
                .thenReturn(result("{\"results\":[{\"name\":\"B.create\",\"file_path\":\"B.java\",\"line\":42}]}"));

        List<Symbol> callers = client.findCallers("r", "A.insert");

        assertThat(callers).hasSize(1);
        assertThat(callers.get(0).name()).isEqualTo("B.create");
        assertThat(callers.get(0).lineNumber()).isEqualTo(42);
    }

    /**
     * 查找类层次。
     */
    @Test
    void findsClassHierarchy() {
        when(mcpClient.callTool(new CallToolRequest("analyze_code_relationships",
                Map.of("query_type", "class_hierarchy", "target", "AssetServiceImpl", "repo_path", "r"))))
                .thenReturn(result("{\"results\":{\"parent_classes\":[{\"name\":\"BaseService\"}]}}"));

        ClassHierarchy hierarchy = client.findClassHierarchy("r", "AssetServiceImpl");

        assertThat(hierarchy.ancestors()).containsExactly("BaseService");
    }

    /**
     * 查找调用链。
     */
    @Test
    void findsCallChain() {
        when(mcpClient.callTool(new CallToolRequest("analyze_code_relationships",
                Map.of("query_type", "call_chain", "target", "A->B", "repo_path", "r"))))
                .thenReturn(result("{\"results\":[{\"symbol\":\"A.main\",\"file\":\"A.java\",\"line\":1},"
                        + "{\"symbol\":\"B.run\",\"file\":\"B.java\",\"line\":2}]}"));

        List<CallStep> chain = client.findCallChain("r", "A", "B");

        assertThat(chain).hasSize(2);
        assertThat(chain.get(1).symbol()).isEqualTo("B.run");
    }

    /**
     * MCP 错误结果映射。
     */
    @Test
    void mapsErrorResult() {
        when(mcpClient.callTool(any(CallToolRequest.class)))
                .thenReturn(new CallToolResult(List.<Content>of(new TextContent("boom")), true));

        assertThatThrownBy(() -> client.findCallers("r", "A"))
                .isInstanceOf(CodeGraphQueryException.class);
    }

    /**
     * 构造文本结果。
     */
    private CallToolResult result(String json) {
        return new CallToolResult(List.<Content>of(new TextContent(json)), false);
    }
}
