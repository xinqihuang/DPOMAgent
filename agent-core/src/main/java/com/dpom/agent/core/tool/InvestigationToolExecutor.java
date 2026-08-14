package com.dpom.agent.core.tool;

import com.dpom.agent.common.codegraph.CallStep;
import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.Symbol;
import com.dpom.agent.common.logtemplate.LogParseResult;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.dpom.agent.common.runtime.ObservationInput;
import com.dpom.agent.common.runtime.RuntimeEvidenceClient;
import com.dpom.agent.core.investigation.ToolAction;
import com.dpom.agent.core.investigation.ToolExecutionResult;
import com.dpom.agent.core.investigation.ToolExecutor;
import com.dpom.agent.core.workspace.CodeWorkspace;
import com.dpom.agent.core.workspace.SearchHit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 调查工具执行器：把工具调用分发到代码图/工作区/运行时证据客户端。
 *
 * <p>绑定到一次调查解析出的快照与工作区。</p>
 */
public class InvestigationToolExecutor implements ToolExecutor {

    private final String snapshotId;
    private final Path workspaceRoot;
    private final String serviceCode;
    private final String environment;
    private final CodeGraphClient codeGraphClient;
    private final CodeWorkspace workspace;
    private final RuntimeEvidenceClient runtimeClient;
    private final LogTemplateMinerClient logTemplateMinerClient;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 构造执行器。
     *
     * @param snapshotId       快照 id
     * @param workspaceRoot    工作区根目录
     * @param serviceCode      服务编码
     * @param environment      环境
     * @param codeGraphClient  代码图客户端
     * @param workspace        代码工作区
     * @param runtimeClient    运行时证据客户端
     */
    public InvestigationToolExecutor(String snapshotId, Path workspaceRoot, String serviceCode, String environment,
                                     CodeGraphClient codeGraphClient, CodeWorkspace workspace,
                                     RuntimeEvidenceClient runtimeClient) {
        this(snapshotId, workspaceRoot, serviceCode, environment, codeGraphClient, workspace, runtimeClient, null);
    }

    /**
     * 构造执行器（含日志模板挖掘客户端）。
     *
     * @param logTemplateMinerClient 日志模板挖掘客户端（可为空）
     */
    public InvestigationToolExecutor(String snapshotId, Path workspaceRoot, String serviceCode, String environment,
                                     CodeGraphClient codeGraphClient, CodeWorkspace workspace,
                                     RuntimeEvidenceClient runtimeClient, LogTemplateMinerClient logTemplateMinerClient) {
        this.snapshotId = snapshotId;
        this.workspaceRoot = workspaceRoot;
        this.serviceCode = serviceCode;
        this.environment = environment;
        this.codeGraphClient = codeGraphClient;
        this.workspace = workspace;
        this.runtimeClient = runtimeClient;
        this.logTemplateMinerClient = logTemplateMinerClient;
    }

    @Override
    public ToolExecutionResult execute(ToolAction action) {
        try {
            return switch (action.name()) {
                case "list_files" -> listFiles(action);
                case "search_text" -> searchText(action);
                case "read_source" -> readSource(action);
                case "find_symbol" -> findSymbol(action);
                case "find_callers" -> findCallers(action);
                case "find_callees" -> findCallees(action);
                case "find_call_chain" -> findCallChain(action);
                case "find_class_hierarchy" -> findClassHierarchy(action);
                case "search_logs" -> searchLogs(action);
                case "query_trace" -> queryTrace(action);
                case "query_alerts" -> queryAlerts(action);
                case "query_metrics" -> queryMetrics(action);
                case "mine_log_templates" -> mineLogTemplates(action);
                default -> throw new IllegalArgumentException("未知工具：" + action.name());
            };
        } catch (Exception e) {
            return new ToolExecutionResult("error", null, "工具 " + action.name() + " 执行失败：" + e.getMessage(),
                    null, null, List.of(), List.of());
        }
    }

    /**
     * 解析入参 JSON。
     */
    private JsonNode args(ToolAction action) throws Exception {
        return mapper.readTree(action.inputJson() == null ? "{}" : action.inputJson());
    }

    private ToolExecutionResult listFiles(ToolAction action) throws Exception {
        String path = args(action).path("path").asText("");
        List<String> files = workspace.listFilesRecursive(workspaceRoot, path, 200);
        return new ToolExecutionResult("workspace", null, "文件列表：" + files, null, null, List.of(), List.of());
    }

    private ToolExecutionResult searchText(ToolAction action) throws Exception {
        String pattern = args(action).path("pattern").asText("");
        List<SearchHit> hits = workspace.searchText(workspaceRoot, pattern, 100);
        return new ToolExecutionResult("workspace", null, "搜索命中：" + hits, null, null, List.of(), List.of());
    }

    private ToolExecutionResult readSource(ToolAction action) throws Exception {
        String path = args(action).path("path").asText("");
        int startLine = args(action).path("startLine").asInt(1);
        String content = workspace.readSource(workspaceRoot, path, startLine, 200, 65536);
        return new ToolExecutionResult("workspace", path, numberLines(content, startLine), null, null, List.of(), List.of());
    }

    /**
     * 给源码行加上「行号|」前缀，便于 LLM 与堆栈行号对照定位异常分支。
     */
    private String numberLines(String content, int startLine) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        String[] lines = content.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(startLine + i).append("| ").append(lines[i]).append("\n");
        }
        return sb.toString();
    }

    private ToolExecutionResult findSymbol(ToolAction action) throws Exception {
        List<Symbol> symbols = codeGraphClient.findSymbol(snapshotId, args(action).path("name").asText(""));
        return new ToolExecutionResult("codegraph", null, "符号：" + symbols, null, null, List.of(), List.of());
    }

    private ToolExecutionResult findCallers(ToolAction action) throws Exception {
        List<Symbol> callers = codeGraphClient.findCallers(snapshotId, args(action).path("symbol").asText(""));
        return new ToolExecutionResult("codegraph", null, "调用方：" + callers, null, null, List.of(), List.of());
    }

    private ToolExecutionResult findCallees(ToolAction action) throws Exception {
        List<Symbol> callees = codeGraphClient.findCallees(snapshotId, args(action).path("symbol").asText(""));
        return new ToolExecutionResult("codegraph", null, "被调用方：" + callees, null, null, List.of(), List.of());
    }

    private ToolExecutionResult findCallChain(ToolAction action) throws Exception {
        List<CallStep> chain = codeGraphClient.findCallChain(snapshotId,
                args(action).path("from").asText(""), args(action).path("to").asText(""));
        return new ToolExecutionResult("codegraph", null, "调用链：" + chain, null, null, List.of(), List.of());
    }

    private ToolExecutionResult findClassHierarchy(ToolAction action) throws Exception {
        var hierarchy = codeGraphClient.findClassHierarchy(snapshotId, args(action).path("class").asText(""));
        return new ToolExecutionResult("codegraph", null, "继承层次：" + hierarchy, null, null, List.of(), List.of());
    }

    private ToolExecutionResult searchLogs(ToolAction action) throws Exception {
        List<ObservationInput> evidence = runtimeClient.searchLogs(serviceCode, environment,
                args(action).path("keyword").asText(""), "1h");
        return new ToolExecutionResult("runtime", null, "日志证据：" + evidence, null, null, List.of(), List.of());
    }

    private ToolExecutionResult queryTrace(ToolAction action) throws Exception {
        List<ObservationInput> evidence = runtimeClient.queryTrace(args(action).path("traceId").asText(""));
        return new ToolExecutionResult("runtime", null, "调用链证据：" + evidence, null, null, List.of(), List.of());
    }

    private ToolExecutionResult queryAlerts(ToolAction action) throws Exception {
        List<ObservationInput> evidence = runtimeClient.queryAlerts(serviceCode, environment, "1h");
        return new ToolExecutionResult("runtime", null, "告警证据：" + evidence, null, null, List.of(), List.of());
    }

    private ToolExecutionResult queryMetrics(ToolAction action) throws Exception {
        List<ObservationInput> evidence = runtimeClient.queryMetrics(serviceCode,
                args(action).path("metric").asText(""), "1h");
        return new ToolExecutionResult("runtime", null, "指标证据：" + evidence, null, null, List.of(), List.of());
    }

    private ToolExecutionResult mineLogTemplates(ToolAction action) throws Exception {
        if (logTemplateMinerClient == null) {
            throw new IllegalStateException("日志模板挖掘客户端未配置");
        }
        List<String> lines = new ArrayList<>();
        JsonNode linesNode = args(action).path("lines");
        if (linesNode.isArray()) {
            linesNode.forEach(item -> lines.add(item.asText()));
        }
        List<LogParseResult> results = logTemplateMinerClient.parseLogs(lines);
        return new ToolExecutionResult("runtime", null, "日志模板：" + results, null, null, List.of(), List.of());
    }
}
