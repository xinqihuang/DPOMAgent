package com.dpom.agent.adapter.codegraph;

import com.dpom.agent.common.codegraph.CallStep;
import com.dpom.agent.common.codegraph.ClassHierarchy;
import com.dpom.agent.common.codegraph.CodeGraphQueryException;
import com.dpom.agent.common.codegraph.Symbol;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CodeGraph MCP 文本结果解析器（版本化，fail closed）。
 *
 * <p>官方 CodeGraph MCP 工具返回文本 Markdown，无 JSON 结构化输出。本解析器锁定固定版本
 * （{@link #FORMAT_VERSION}）的文本格式：search/callers/callees/impact 的标题与列表结构必须匹配，
 * 未知或畸形格式抛 {@link CodeGraphQueryException}；call chain / class hierarchy 无法可靠解析时安全降级
 * 返回空并记录原因，绝不伪造。</p>
 */
public class CodeGraphResponseParser {

    /** 解析器锁定的 CodeGraph 输出格式版本。 */
    public static final String FORMAT_VERSION = "codegraph-1.5.0";

    private static final Pattern SEARCH_HEADER = Pattern.compile("^\\*\\*Search Results \\((\\d+) found\\)\\*\\*");
    private static final Pattern SYMBOL_HEADER = Pattern.compile("^\\*\\*(.+?)\\*\\* \\(([^)]+)\\)$");
    private static final Pattern LOCATION = Pattern.compile("^([^:\\s]+?)(?::(\\d+))?$");
    private static final Pattern CALLER_CALLEE_LINE =
            Pattern.compile("^- (.+?) \\(([^)]+)\\) - ([^\\s]+?)(?::(\\d+))?(?: — .*)?$");
    private static final Pattern IMPACT_HEADER = Pattern.compile("^\\*\\*Impact: .+ affects (\\d+) symbols?\\*\\*");
    private static final Pattern IMPACT_FILE = Pattern.compile("^\\*\\*([^*]+):\\*\\*$");

    /**
     * 解析 codegraph_search 结果。
     *
     * @param text MCP 文本结果
     * @return 符号列表
     */
    public List<Symbol> parseSearch(String text) {
        if (isNoResult(text)) {
            return List.of();
        }
        requireHeader(text, SEARCH_HEADER, "codegraph_search");
        List<Symbol> symbols = new ArrayList<>();
        String[] lines = split(text);
        for (int i = 0; i < lines.length; i++) {
            Matcher header = SYMBOL_HEADER.matcher(lines[i]);
            if (!header.matches()) {
                continue;
            }
            String name = header.group(1).trim();
            String kind = header.group(2).trim();
            String filePath = null;
            Integer line = null;
            if (i + 1 < lines.length) {
                Matcher loc = LOCATION.matcher(lines[i + 1].trim());
                if (loc.matches()) {
                    filePath = loc.group(1);
                    line = loc.group(2) == null ? null : Integer.valueOf(loc.group(2));
                }
            }
            if (filePath == null) {
                throw new CodeGraphQueryException("codegraph_search 结果缺少文件路径：" + lines[i]);
            }
            symbols.add(new Symbol(name, kind, filePath, line));
        }
        return symbols;
    }

    /**
     * 解析 codegraph_callers / codegraph_callees 结果。
     *
     * @param text MCP 文本结果
     * @return 符号列表
     */
    public List<Symbol> parseCallerCallees(String text) {
        if (isNoResult(text)) {
            return List.of();
        }
        if (!text.contains("**Callers of ") && !text.contains("**Callees of ")) {
            throw new CodeGraphQueryException("codegraph_callers/callees 结果格式未知");
        }
        List<Symbol> symbols = new ArrayList<>();
        for (String line : split(text)) {
            Matcher m = CALLER_CALLEE_LINE.matcher(line.trim());
            if (!m.matches()) {
                continue;
            }
            String name = m.group(1).trim();
            String kind = m.group(2).trim();
            String filePath = m.group(3).trim();
            Integer lineNumber = m.group(4) == null ? null : Integer.valueOf(m.group(4));
            symbols.add(new Symbol(name, kind, filePath, lineNumber));
        }
        return symbols;
    }

    /**
     * 解析 codegraph_impact 结果（有界图摘要）。
     *
     * @param text MCP 文本结果
     * @return 受影响符号列表
     */
    public List<Symbol> parseImpact(String text) {
        if (isNoResult(text)) {
            return List.of();
        }
        requireHeader(text, IMPACT_HEADER, "codegraph_impact");
        List<Symbol> symbols = new ArrayList<>();
        String currentFile = null;
        for (String raw : split(text)) {
            String line = raw.trim();
            Matcher fileMatcher = IMPACT_FILE.matcher(line);
            if (fileMatcher.matches()) {
                currentFile = fileMatcher.group(1).trim();
                continue;
            }
            if (currentFile != null && !line.isEmpty() && !line.startsWith("**")) {
                for (String token : line.split(",")) {
                    Matcher entry = Pattern.compile("^([^:]+):(\\d+)$").matcher(token.trim());
                    if (entry.matches()) {
                        symbols.add(new Symbol(entry.group(1).trim(), "symbol", currentFile,
                                Integer.valueOf(entry.group(2))));
                    }
                }
            }
        }
        return symbols;
    }

    /**
     * 解析 codegraph_explore 文本中的结构化 call paths（尽量）。
     *
     * <p>不可可靠解析时安全降级返回空列表，不伪造。</p>
     *
     * @param text MCP 文本结果
     * @return 调用链步骤（可为空）
     */
    public List<CallStep> parseCallChain(String text) {
        List<CallStep> steps = new ArrayList<>();
        for (String raw : split(text)) {
            String line = raw.trim();
            Matcher m = CALLER_CALLEE_LINE.matcher(line);
            if (m.matches()) {
                String name = m.group(1).trim();
                String filePath = m.group(3).trim();
                Integer lineNumber = m.group(4) == null ? null : Integer.valueOf(m.group(4));
                steps.add(new CallStep(name, filePath, lineNumber));
            }
        }
        return steps.size() >= 2 ? steps : List.of();
    }

    /**
     * 解析 codegraph_node 文本中的类层次（从签名推断祖先，尽量）。
     *
     * <p>不可可靠解析时安全降级返回空祖先列表，不伪造。</p>
     *
     * @param text      MCP 文本结果
     * @param className 类名
     * @return 继承层次
     */
    public ClassHierarchy parseClassHierarchy(String text, String className) {
        List<String> ancestors = new ArrayList<>();
        for (String raw : split(text)) {
            String line = raw.trim();
            if (line.startsWith("**Signature:**")) {
                Matcher ext = Pattern.compile("extends\\s+([A-Za-z_$][\\w.$]*)").matcher(line);
                if (ext.find()) {
                    ancestors.add(ext.group(1));
                }
                Matcher imp = Pattern.compile("implements\\s+([A-Za-z_$][\\w.$]*(?:\\s*,\\s*[A-Za-z_$][\\w.$]*)*)").matcher(line);
                if (imp.find()) {
                    for (String part : imp.group(1).split(",")) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            ancestors.add(trimmed);
                        }
                    }
                }
            }
        }
        return new ClassHierarchy(className, ancestors);
    }

    /**
     * 解析器锁定的格式版本。
     *
     * @return 格式版本
     */
    public String formatVersion() {
        return FORMAT_VERSION;
    }

    private static boolean isNoResult(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.startsWith("No results found for")
                || text.startsWith("Symbol \"") && text.contains("not found in the codebase")
                || text.startsWith("No callers found for")
                || text.startsWith("No callees found for");
    }

    private static void requireHeader(String text, Pattern header, String tool) {
        for (String line : split(text)) {
            if (header.matcher(line).matches()) {
                return;
            }
        }
        throw new CodeGraphQueryException(tool + " 结果格式未知（期望标题未找到）");
    }

    private static String[] split(String text) {
        return text == null ? new String[0] : text.split("\\r?\\n");
    }
}
