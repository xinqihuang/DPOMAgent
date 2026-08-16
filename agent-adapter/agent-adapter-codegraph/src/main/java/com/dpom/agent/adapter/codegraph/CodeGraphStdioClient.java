package com.dpom.agent.adapter.codegraph;

import com.dpom.agent.common.codegraph.CallStep;
import com.dpom.agent.common.codegraph.ClassHierarchy;
import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeGraphQueryException;
import com.dpom.agent.common.codegraph.CodeGraphTimeoutException;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.RegisteredRepository;
import com.dpom.agent.common.codegraph.RepositoryRegistry;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.common.codegraph.Symbol;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 通过 CodeGraph（colbymchenry/codegraph）官方 stdio MCP 访问代码图的客户端。
 *
 * <p>把 codegraph_search/callers/callees/impact/node/explore 的文本结果解析为内部 DTO；
 * resolveSnapshot 由 Repository Registry 确定映射；每次查询都把 snapshotId 经 Registry 反查校验为受控
 * projectPath，禁止任意路径直接进入 MCP 参数。</p>
 */
public class CodeGraphStdioClient implements CodeGraphClient {

    private final Supplier<McpSyncClient> mcpClientSupplier;
    private final RepositoryRegistry repositoryRegistry;
    private final CodeGraphResponseParser parser;
    private volatile McpSyncClient mcpClient;

    /**
     * 构造客户端。
     *
     * @param mcpClientSupplier MCP 客户端供应器（stdio 连接在首次使用时建立）
     * @param repositoryRegistry 仓库注册表（解析快照根目录 + 校验 projectPath）
     */
    public CodeGraphStdioClient(Supplier<McpSyncClient> mcpClientSupplier, RepositoryRegistry repositoryRegistry) {
        this(mcpClientSupplier, repositoryRegistry, new CodeGraphResponseParser());
    }

    /**
     * 构造客户端（可注入解析器）。
     *
     * @param mcpClientSupplier MCP 客户端供应器
     * @param repositoryRegistry 仓库注册表
     * @param parser            文本解析器
     */
    public CodeGraphStdioClient(Supplier<McpSyncClient> mcpClientSupplier, RepositoryRegistry repositoryRegistry,
                                CodeGraphResponseParser parser) {
        this.mcpClientSupplier = mcpClientSupplier;
        this.repositoryRegistry = repositoryRegistry;
        this.parser = parser;
    }

    /**
     * 首次使用时构建并初始化 MCP 连接。
     */
    private synchronized void ensureInitialized() {
        if (mcpClient == null) {
            mcpClient = mcpClientSupplier.get();
            try {
                mcpClient.initialize();
            } catch (RuntimeException e) {
                mcpClient = null;
                throw mapException(e);
            }
        }
    }

    @Override
    public CodeSnapshot resolveSnapshot(String serviceCode, String commitSha) {
        RegisteredRepository repo = repositoryRegistry.resolve(serviceCode, commitSha);
        String root = repo.snapshotRoot().toString();
        return new CodeSnapshot(root, serviceCode, commitSha, root, SnapshotStatus.READY);
    }

    @Override
    public CodeSnapshot getSnapshot(String snapshotId) {
        RegisteredRepository repo = repositoryRegistry.resolveByProjectPath(snapshotId);
        return new CodeSnapshot(snapshotId, repo.serviceCode(), repo.commitSha(), snapshotId, SnapshotStatus.READY);
    }

    @Override
    public List<Symbol> findSymbol(String snapshotId, String name) {
        String projectPath = resolveProjectPath(snapshotId);
        String text = callToolText("codegraph_search", Map.of("query", name, "projectPath", projectPath));
        return parser.parseSearch(text);
    }

    @Override
    public List<Symbol> findCallers(String snapshotId, String symbol) {
        String projectPath = resolveProjectPath(snapshotId);
        String text = callToolText("codegraph_callers", Map.of("symbol", symbol, "projectPath", projectPath));
        return parser.parseCallerCallees(text);
    }

    @Override
    public List<Symbol> findCallees(String snapshotId, String symbol) {
        String projectPath = resolveProjectPath(snapshotId);
        String text = callToolText("codegraph_callees", Map.of("symbol", symbol, "projectPath", projectPath));
        return parser.parseCallerCallees(text);
    }

    @Override
    public List<CallStep> findCallChain(String snapshotId, String fromSymbol, String toSymbol) {
        String projectPath = resolveProjectPath(snapshotId);
        String text = callToolText("codegraph_explore",
                Map.of("query", fromSymbol + " " + toSymbol, "projectPath", projectPath));
        return parser.parseCallChain(text);
    }

    @Override
    public ClassHierarchy findClassHierarchy(String snapshotId, String className) {
        String projectPath = resolveProjectPath(snapshotId);
        String text = callToolText("codegraph_node",
                Map.of("symbol", className, "includeCode", false, "projectPath", projectPath));
        return parser.parseClassHierarchy(text, className);
    }

    @Override
    public List<Symbol> findImpact(String snapshotId, String symbol) {
        String projectPath = resolveProjectPath(snapshotId);
        String text = callToolText("codegraph_impact", Map.of("symbol", symbol, "projectPath", projectPath));
        return parser.parseImpact(text);
    }

    /**
     * 把 snapshotId 经 Repository Registry 反查并验证为受控 projectPath，禁止任意路径直接进入 MCP 参数。
     */
    private String resolveProjectPath(String snapshotId) {
        return repositoryRegistry.resolveByProjectPath(snapshotId).snapshotRoot().toString();
    }

    /**
     * 调用 MCP 工具并提取文本内容。
     */
    private String callToolText(String name, Map<String, Object> arguments) {
        ensureInitialized();
        final CallToolResult result;
        try {
            result = mcpClient.callTool(new CallToolRequest(name, arguments));
        } catch (RuntimeException e) {
            throw mapException(e);
        }
        if (Boolean.TRUE.equals(result.isError())) {
            throw new CodeGraphQueryException("CodeGraph MCP 工具 " + name + " 返回错误");
        }
        return result.content().stream()
                .filter(content -> content instanceof TextContent)
                .map(content -> ((TextContent) content).text())
                .findFirst()
                .orElseThrow(() -> new CodeGraphQueryException("CodeGraph MCP 工具 " + name + " 无文本结果"));
    }

    /**
     * 把底层异常映射为内部异常（超时 / transport closed / 其他）。
     */
    private static RuntimeException mapException(RuntimeException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.toLowerCase().contains("timeout") || message.toLowerCase().contains("timed out")) {
            return new CodeGraphTimeoutException("CodeGraph 调用超时", e);
        }
        return new CodeGraphQueryException("CodeGraph 调用失败：" + message, e);
    }
}
