package com.dpom.agent.adapter.runtime;

import com.dpom.agent.common.logtemplate.LogParameter;
import com.dpom.agent.common.logtemplate.LogParseResult;
import com.dpom.agent.common.logtemplate.LogTemplate;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.dpom.agent.common.runtime.RuntimeEvidenceQueryException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 通过 MCP Server（Drain3）访问日志模板挖掘的客户端。
 */
public class McpLogTemplateMinerClient implements LogTemplateMinerClient {

    private final Supplier<McpSyncClient> mcpClientSupplier;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile McpSyncClient mcpClient;

    /**
     * 构造客户端。
     *
     * @param mcpClientSupplier MCP 客户端供应器（连接在首次使用时建立）
     */
    public McpLogTemplateMinerClient(Supplier<McpSyncClient> mcpClientSupplier) {
        this.mcpClientSupplier = mcpClientSupplier;
    }

    /**
     * 首次使用时构建并初始化 MCP 连接。
     */
    private synchronized void ensureInitialized() {
        if (mcpClient == null) {
            mcpClient = mcpClientSupplier.get();
            mcpClient.initialize();
        }
    }

    @Override
    public List<LogParseResult> parseLogs(List<String> lines) {
        JsonNode resp = callTool("train_logs", Map.of("log_messages", lines));
        List<LogParseResult> results = new ArrayList<>();
        for (JsonNode item : resp.path("results")) {
            List<LogParameter> params = new ArrayList<>();
            for (JsonNode param : item.path("parameters")) {
                params.add(new LogParameter(param.path("value").asText(), param.path("mask_name").asText()));
            }
            results.add(new LogParseResult(item.path("cluster_id").asInt(), item.path("cluster_size").asInt(),
                    item.path("template").asText(), params));
        }
        return results;
    }

    @Override
    public List<LogTemplate> listTemplates() {
        JsonNode resp = callTool("list_templates", Map.of("limit", 1000));
        List<LogTemplate> templates = new ArrayList<>();
        for (JsonNode item : resp.path("templates")) {
            templates.add(new LogTemplate(item.path("cluster_id").asInt(), item.path("cluster_size").asInt(),
                    item.path("template").asText()));
        }
        return templates;
    }

    /**
     * 调用 MCP 工具并解析 JSON 文本结果。
     */
    private JsonNode callTool(String name, Map<String, Object> arguments) {
        ensureInitialized();
        CallToolResult result = mcpClient.callTool(new CallToolRequest(name, arguments));
        if (Boolean.TRUE.equals(result.isError())) {
            throw new RuntimeEvidenceQueryException("日志模板挖掘 MCP 工具 " + name + " 返回错误");
        }
        String text = result.content().stream()
                .filter(content -> content instanceof TextContent)
                .map(content -> ((TextContent) content).text())
                .findFirst()
                .orElse("{}");
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            throw new RuntimeEvidenceQueryException("解析日志模板挖掘 MCP 结果失败", e);
        }
    }
}
