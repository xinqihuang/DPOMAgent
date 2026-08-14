package com.dpom.agent.core.stacktrace;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.Symbol;
import com.dpom.agent.core.workspace.CodeWorkspace;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 堆栈调查器：stacktrace → 解析 → 解析快照 → 读源码 → 代码图导航 → 根因结论。
 */
@Service
public class StacktraceInvestigator {

    private static final int MAX_LINES = 200;

    private static final long MAX_BYTES = 65536;

    private final CodeGraphClient codeGraphClient;
    private final CodeWorkspace workspace;
    private final StacktraceParser parser;

    /**
     * 构造器注入。
     *
     * @param codeGraphClient 代码图客户端
     * @param workspace       代码工作区
     * @param parser          堆栈解析器
     */
    public StacktraceInvestigator(CodeGraphClient codeGraphClient, CodeWorkspace workspace,
                                  StacktraceParser parser) {
        this.codeGraphClient = codeGraphClient;
        this.workspace = workspace;
        this.parser = parser;
    }

    /**
     * 执行堆栈调查。
     *
     * @param serviceCode 服务编码
     * @param commitSha   提交 SHA
     * @param stacktrace  堆栈文本
     * @return 调查报告
     */
    public StacktraceReport investigate(String serviceCode, String commitSha, String stacktrace) {
        List<StackFrame> frames = parser.parse(stacktrace);
        if (frames.isEmpty()) {
            throw new IllegalStateException("堆栈中未找到应用栈帧");
        }
        CodeSnapshot snapshot = codeGraphClient.resolveSnapshot(serviceCode, commitSha);
        Path root = Path.of(snapshot.workspacePath());

        StackFrame top = frames.get(0);
        List<SourceEvidence> sourceEvidence = new ArrayList<>();
        String content = workspace.readSource(root, top.fileName(), MAX_LINES, MAX_BYTES);
        String lineContent = lineAt(content, top.lineNumber());
        sourceEvidence.add(new SourceEvidence(top.fileName(), top.lineNumber(), lineContent));

        String qualified = top.className() + "." + top.methodName();
        List<GraphEvidence> graphEvidence = new ArrayList<>();
        List<Symbol> callers = codeGraphClient.findCallers(snapshot.snapshotId(), qualified);
        graphEvidence.add(new GraphEvidence("findCallers", qualified, callers));

        String rootCause = "根因位于 " + top.fileName() + ":" + top.lineNumber() + "（" + qualified
                + "），源码行：[" + lineContent + "]";
        return new StacktraceReport(snapshot, frames, sourceEvidence, graphEvidence, rootCause);
    }

    /**
     * 取指定行内容。
     */
    private String lineAt(String content, Integer lineNumber) {
        if (lineNumber == null) {
            return "";
        }
        String[] lines = content.split("\\n", -1);
        if (lineNumber > 0 && lineNumber <= lines.length) {
            return lines[lineNumber - 1].trim();
        }
        return "";
    }
}
