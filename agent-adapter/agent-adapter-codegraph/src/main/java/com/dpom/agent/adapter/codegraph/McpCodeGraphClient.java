package com.dpom.agent.adapter.codegraph;

import com.dpom.agent.common.codegraph.CallStep;
import com.dpom.agent.common.codegraph.ClassHierarchy;
import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeGraphQueryException;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotNotFoundException;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.common.codegraph.Symbol;
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
 * 通过 MCP Server（CodeGraphContext）访问代码图的客户端。
 *
 * <p>把 CGC 的 MCP 工具结果解析为内部 DTO。</p>
 */
public class McpCodeGraphClient implements CodeGraphClient {

    private final Supplier<McpSyncClient> mcpClientSupplier;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile McpSyncClient mcpClient;

    /**
     * 构造客户端。
     *
     * @param mcpClientSupplier MCP 客户端供应器（连接在首次使用时建立）
     */
    public McpCodeGraphClient(Supplier<McpSyncClient> mcpClientSupplier) {
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
    public CodeSnapshot resolveSnapshot(String serviceCode, String commitSha) {
        JsonNode resp = callTool("list_indexed_repositories", Map.of());
        List<String> paths = new ArrayList<>();
        JsonNode repos = resp.path("repositories");
        if (repos.isArray()) {
            for (JsonNode repo : repos) {
                if (repo.isTextual()) {
                    paths.add(repo.asText());
                } else {
                    String path = textOf(repo, "path", "repo_path", "name");
                    if (path != null) {
                        paths.add(path);
                    }
                }
            }
        }
        String repo = paths.stream().filter(path -> path.contains(serviceCode)).findFirst()
                .orElse(paths.isEmpty() ? null : paths.get(0));
        if (repo == null) {
            throw new SnapshotNotFoundException("未找到索引仓库：" + serviceCode);
        }
        return new CodeSnapshot(repo, serviceCode, commitSha, repo, SnapshotStatus.READY);
    }

    @Override
    public CodeSnapshot getSnapshot(String snapshotId) {
        return new CodeSnapshot(snapshotId, null, null, snapshotId, SnapshotStatus.READY);
    }

    @Override
    public List<Symbol> findSymbol(String snapshotId, String name) {
        JsonNode resp = callTool("find_code", Map.of("query", name, "repo_path", snapshotId));
        JsonNode results = resp.path("results");
        return parseSymbols(results.isArray() ? results : resp.path("data").path("results"));
    }

    @Override
    public List<Symbol> findCallers(String snapshotId, String symbol) {
        JsonNode resp = callTool("analyze_code_relationships",
                Map.of("query_type", "find_callers", "target", symbol, "repo_path", snapshotId));
        return parseSymbols(resp.path("results"));
    }

    @Override
    public List<Symbol> findCallees(String snapshotId, String symbol) {
        JsonNode resp = callTool("analyze_code_relationships",
                Map.of("query_type", "find_callees", "target", symbol, "repo_path", snapshotId));
        return parseSymbols(resp.path("results"));
    }

    @Override
    public List<CallStep> findCallChain(String snapshotId, String fromSymbol, String toSymbol) {
        JsonNode resp = callTool("analyze_code_relationships",
                Map.of("query_type", "call_chain", "target", fromSymbol + "->" + toSymbol, "repo_path", snapshotId));
        JsonNode results = resp.path("results");
        JsonNode steps = results.isArray() ? results : results.path("steps");
        List<CallStep> chain = new ArrayList<>();
        if (steps.isArray()) {
            for (JsonNode item : steps) {
                chain.add(new CallStep(textOrEmpty(item, "symbol", "name", "function", "caller", "callee"),
                        textOf(item, "file_path", "file", "path"), intOf(item, "line", "line_number", "lineNumber")));
            }
        }
        return chain;
    }

    @Override
    public ClassHierarchy findClassHierarchy(String snapshotId, String className) {
        JsonNode resp = callTool("analyze_code_relationships",
                Map.of("query_type", "class_hierarchy", "target", className, "repo_path", snapshotId));
        JsonNode results = resp.path("results");
        List<String> ancestors = new ArrayList<>();
        JsonNode parents = results.path("parent_classes");
        if (parents.isArray()) {
            for (JsonNode parent : parents) {
                ancestors.add(textOrEmpty(parent, "name", "class", "class_name"));
            }
        }
        return new ClassHierarchy(className, ancestors);
    }

    /**
     * 调用 MCP 工具并解析 JSON 文本结果。
     */
    private JsonNode callTool(String name, Map<String, Object> arguments) {
        ensureInitialized();
        CallToolResult result = mcpClient.callTool(new CallToolRequest(name, arguments));
        if (Boolean.TRUE.equals(result.isError())) {
            throw new CodeGraphQueryException("代码图 MCP 工具 " + name + " 返回错误");
        }
        String text = result.content().stream()
                .filter(content -> content instanceof TextContent)
                .map(content -> ((TextContent) content).text())
                .findFirst()
                .orElse("{}");
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            throw new CodeGraphQueryException("解析代码图 MCP 结果失败", e);
        }
    }

    /**
     * 把数组节点解析为符号列表（宽容字段名）。
     */
    private List<Symbol> parseSymbols(JsonNode node) {
        List<Symbol> symbols = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                symbols.add(new Symbol(textOrEmpty(item, "name", "function", "symbol", "function_name", "method"),
                        textOf(item, "kind", "type"), textOf(item, "file_path", "file", "path", "target_file_path"),
                        intOf(item, "line", "line_number", "lineNumber")));
            }
        }
        return symbols;
    }

    /**
     * 宽容地取第一个存在的文本字段。
     */
    private String textOf(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isValueNode()) {
                return value.asText();
            }
        }
        return null;
    }

    /**
     * 宽容地取第一个存在的文本字段（缺省空串）。
     */
    private String textOrEmpty(JsonNode node, String... keys) {
        String text = textOf(node, keys);
        return text == null ? "" : text;
    }

    /**
     * 宽容地取第一个存在的数字字段。
     */
    private Integer intOf(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isNumber()) {
                return value.asInt();
            }
        }
        return null;
    }
}
