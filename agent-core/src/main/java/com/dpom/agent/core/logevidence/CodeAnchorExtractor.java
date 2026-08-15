package com.dpom.agent.core.logevidence;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码锚点提取器：用确定性正则规则从模板与代表样本中提取异常、栈帧、类/方法、HTTP 路径、mapper id 与日志常量。
 *
 * <p>不引入 RAG/Embedding/Vector DB。</p>
 */
public class CodeAnchorExtractor {

    private static final String RULE_VERSION = "v1";
    private static final Pattern EXCEPTION = Pattern.compile("(?:[a-zA-Z_$][\\w$]*\\.)+[A-Z]\\w*(?:Exception|Error)");
    private static final Pattern STACK_FRAME = Pattern.compile("at\\s+[\\w.$]+\\.[\\w$]+\\([\\w.$]+\\.java:\\d+\\)");
    private static final Pattern CLASS_METHOD = Pattern.compile("[a-zA-Z_$][\\w$]*(?:\\.[a-zA-Z_$][\\w$]*)+\\.[a-zA-Z_$][\\w$]*\\(");
    private static final Pattern HTTP = Pattern.compile("\\b(GET|POST|PUT|DELETE|PATCH)\\s+/\\S+");
    private static final Pattern MAPPER = Pattern.compile("[\\w.$]*Mapper(?:\\.[\\w$]+)?");

    /**
     * 提取代码锚点。
     *
     * @param evidence 日志证据
     * @return 去重后的锚点列表
     */
    public List<CodeAnchor> extract(LogEvidence evidence) {
        String text = evidence.summary().template() + "\n" + String.join("\n", evidence.summary().representativeSamples());
        String sid = evidence.evidenceId();
        List<CodeAnchor> anchors = new ArrayList<>();
        collect(anchors, EXCEPTION, text, "EXCEPTION", sid, 0.9);
        collect(anchors, STACK_FRAME, text, "STACK_FRAME", sid, 0.95);
        collectClassMethods(anchors, text, sid);
        collect(anchors, HTTP, text, "HTTP_PATH", sid, 0.9);
        collect(anchors, MAPPER, text, "MAPPER_ID", sid, 0.85);
        if (!evidence.summary().template().isBlank()) {
            anchors.add(new CodeAnchor("LOG_CONSTANT", evidence.summary().template(), sid, 0.5, RULE_VERSION));
        }
        return dedupe(anchors);
    }

    /**
     * 用正则收集锚点（取整体匹配）。
     */
    private void collect(List<CodeAnchor> anchors, Pattern pattern, String text, String type, String sid,
                         double confidence) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            anchors.add(new CodeAnchor(type, m.group(), sid, confidence, RULE_VERSION));
        }
    }

    /**
     * 收集类.方法引用，去掉尾部左括号。
     */
    private void collectClassMethods(List<CodeAnchor> anchors, String text, String sid) {
        Matcher m = CLASS_METHOD.matcher(text);
        while (m.find()) {
            String raw = m.group();
            anchors.add(new CodeAnchor("CLASS_METHOD", raw.substring(0, raw.length() - 1), sid, 0.6, RULE_VERSION));
        }
    }

    /**
     * 按 type+value 去重，保持顺序。
     */
    private List<CodeAnchor> dedupe(List<CodeAnchor> anchors) {
        Set<String> seen = new LinkedHashSet<>();
        List<CodeAnchor> out = new ArrayList<>();
        for (CodeAnchor a : anchors) {
            if (seen.add(a.type() + "\u0000" + a.value())) {
                out.add(a);
            }
        }
        return out;
    }
}
