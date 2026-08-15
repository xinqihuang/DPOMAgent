package com.dpom.agent.core.tool;

import com.dpom.agent.common.llm.ToolDefinition;

import java.util.List;

/**
 * 调查工具集：暴露给 LLM 的代码/工作区/运行时工具定义。
 *
 * <p>明确不包含 execute_shell。</p>
 */
public final class Toolset {

    private Toolset() {
    }

    /**
     * 全部工具定义。
     *
     * @return 工具定义列表
     */
    public static List<ToolDefinition> definitions() {
        return List.of(
                tool("list_files", "列出快照工作区内的文件", "path"),
                tool("search_text", "在快照工作区内搜索文本", "pattern"),
                new ToolDefinition("read_source",
                        "读取快照工作区内的源码文件。path 为相对文件名；startLine 为可选起始行（1 开始），"
                                + "当堆栈给出 文件名:行号 时用它读取该行附近的方法体",
                        "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},"
                                + "\"startLine\":{\"type\":\"integer\"}},\"required\":[\"path\"]}"),
                tool("find_symbol", "查找代码符号", "name"),
                tool("find_callers", "查找指定符号的调用方", "symbol"),
                tool("find_callees", "查找指定符号的被调用方", "symbol"),
                tool("find_call_chain", "查找两个符号之间的调用链", "from"),
                tool("find_class_hierarchy", "查找类的继承层次", "class"),
                tool("search_logs", "搜索应用日志", "keyword"),
                tool("query_trace", "查询 APM 调用链", "traceId"),
                tool("query_alerts", "查询告警/事件摘要", "timeRange"),
                tool("query_metrics", "查询指标", "metric"),
                new ToolDefinition("mine_log_templates",
                        "把应用日志聚类为模板并抽取参数。lines 为应用日志行数组（每条一行）",
                        "{\"type\":\"object\",\"properties\":{\"lines\":{\"type\":\"array\","
                                + "\"items\":{\"type\":\"string\"}}},\"required\":[\"lines\"]}")
        );
    }

    /**
     * 构造单参数工具定义。
     */
    private static ToolDefinition tool(String name, String description, String param) {
        String schema = "{\"type\":\"object\",\"properties\":{\"" + param + "\":{\"type\":\"string\"}}}";
        return new ToolDefinition(name, description, schema);
    }
}
