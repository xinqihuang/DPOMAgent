package com.dpom.agent.adapter.runtime;

import com.dpom.agent.common.logtemplate.LogParseResult;
import com.dpom.agent.common.logtemplate.LogTemplate;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MCP 日志模板挖掘客户端验收：train_logs / list_templates（对接 Drain3MCPServer）。
 */
class McpLogTemplateMinerClientTest {

    private McpSyncClient mcpClient;

    private McpLogTemplateMinerClient client;

    @BeforeEach
    void setUp() {
        mcpClient = mock(McpSyncClient.class);
        client = new McpLogTemplateMinerClient(() -> mcpClient);
    }

    /**
     * 训练批量日志返回模板与参数。
     */
    @Test
    void parsesLogs() {
        when(mcpClient.callTool(new CallToolRequest("train_logs", Map.of("log_messages", List.of("a", "b")))))
                .thenReturn(result("{\"mode\":\"train\",\"total\":1,\"matched\":1,\"unmatched\":0,\"changed\":1,"
                        + "\"results\":[{\"log_message\":\"a\",\"matched\":true,\"cluster_id\":1,\"cluster_size\":2,"
                        + "\"template\":\"connected to <IP>\",\"change_type\":\"cluster_created\","
                        + "\"parameters\":[{\"value\":\"10.0.0.1\",\"mask_name\":\"IP\"}]}]}"));

        List<LogParseResult> results = client.parseLogs(List.of("a", "b"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).template()).isEqualTo("connected to <IP>");
        assertThat(results.get(0).params().get(0).mask()).isEqualTo("IP");
    }

    /**
     * 列出模板。
     */
    @Test
    void listsTemplates() {
        when(mcpClient.callTool(new CallToolRequest("list_templates", Map.of("limit", 1000))))
                .thenReturn(result("{\"total\":1,\"offset\":0,\"limit\":1000,"
                        + "\"templates\":[{\"cluster_id\":1,\"cluster_size\":2,\"template\":\"connected to <IP>\"}]}"));

        List<LogTemplate> templates = client.listTemplates();

        assertThat(templates).hasSize(1);
        assertThat(templates.get(0).template()).isEqualTo("connected to <IP>");
    }

    /**
     * 构造文本结果。
     */
    private CallToolResult result(String json) {
        return new CallToolResult(List.<Content>of(new TextContent(json)), false);
    }
}
