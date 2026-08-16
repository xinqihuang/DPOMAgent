package com.dpom.agent.core.logevidence;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeGraphException;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.common.codegraph.Symbol;
import com.dpom.agent.core.workspace.CodeWorkspace;
import com.dpom.agent.core.workspace.SearchHit;
import com.dpom.agent.core.workspace.WorkspaceException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 版本绑定代码证据解析器：CodeGraph 只做候选导航，事实源码必须来自与 Incident 同一 commit 的 Snapshot。
 */
public class CodeEvidenceResolver {

    private final CodeGraphClient codeGraphClient;
    private final CodeWorkspace workspace;

    /**
     * 构造解析器。
     *
     * @param codeGraphClient 代码图客户端
     * @param workspace       受控代码工作区
     */
    public CodeEvidenceResolver(CodeGraphClient codeGraphClient, CodeWorkspace workspace) {
        this.codeGraphClient = codeGraphClient;
        this.workspace = workspace;
    }

    /**
     * 解析锚点为版本绑定代码证据。
     *
     * @param incidentCommit Incident 的提交 SHA
     * @param snapshot       代码快照
     * @param anchors        代码锚点
     * @return 代码证据列表（降级时返回状态标记）
     */
    public List<CodeEvidence> resolve(String incidentCommit, CodeSnapshot snapshot, List<CodeAnchor> anchors) {
        List<CodeEvidence> out = new ArrayList<>();
        if (snapshot.status() != SnapshotStatus.READY) {
            out.add(degraded(snapshot, "NOT_READY"));
            return out;
        }
        if (incidentCommit != null && !incidentCommit.isBlank() && !incidentCommit.equals(snapshot.commitSha())) {
            out.add(degraded(snapshot, "VERSION_MISMATCH"));
            return out;
        }
        AtomicInteger seq = new AtomicInteger(0);
        for (CodeAnchor anchor : anchors) {
            if (isSymbolAnchor(anchor.type())) {
                resolveAnchor(anchor, snapshot, seq, out);
            }
        }
        return out;
    }

    /**
     * 符号类锚点类型。
     */
    private boolean isSymbolAnchor(String type) {
        return "EXCEPTION".equals(type) || "STACK_FRAME".equals(type) || "CLASS_METHOD".equals(type)
                || "MAPPER_ID".equals(type) || "LOGGER".equals(type);
    }

    /**
     * 解析单个锚点：先查代码图，图不可用时降级为工作区内文本搜索。
     */
    private void resolveAnchor(CodeAnchor anchor, CodeSnapshot snapshot, AtomicInteger seq, List<CodeEvidence> out) {
        String query = queryFor(anchor);
        try {
            List<Symbol> symbols = codeGraphClient.findSymbol(snapshot.snapshotId(), query);
            if (symbols.isEmpty()) {
                symbols = codeGraphClient.findCallers(snapshot.snapshotId(), query);
            }
            for (Symbol symbol : symbols) {
                String excerpt = readExcerpt(snapshot.workspacePath(), symbol.filePath(), symbol.lineNumber());
                out.add(new CodeEvidence("code-" + seq.incrementAndGet(), anchor.value(), identifier(anchor),
                        symbol.filePath(), symbol.lineNumber(), snapshot.commitSha(), excerpt, "VERIFIED"));
            }
        } catch (CodeGraphException e) {
            for (SearchHit hit : workspace.searchText(Path.of(snapshot.workspacePath()), anchor.value(), 5)) {
                out.add(new CodeEvidence("code-" + seq.incrementAndGet(), anchor.value(), identifier(anchor),
                        hit.filePath(), hit.lineNumber(), snapshot.commitSha(), hit.line(), "WORKSPACE_FALLBACK"));
            }
        }
    }

    /**
     * 从锚点值提取稳定的「类.方法」标识（如 AssetRepository.insert）。
     */
    private String identifier(CodeAnchor anchor) {
        String v = anchor.value() == null ? "" : anchor.value().strip();
        if (v.startsWith("at ")) {
            v = v.substring(3);
            int paren = v.indexOf('(');
            if (paren >= 0) {
                v = v.substring(0, paren);
            }
        }
        String[] parts = v.split("\\.");
        boolean method = "CLASS_METHOD".equals(anchor.type()) || "STACK_FRAME".equals(anchor.type())
                || "MAPPER_ID".equals(anchor.type());
        if (parts.length == 0) {
            return v;
        }
        if (method && parts.length >= 2) {
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        }
        return parts[parts.length - 1];
    }

    /**
     * 把锚点值规约为可精确匹配的短名（CodeGraph codegraph_search 按短名精确匹配）。
     */
    private String queryFor(CodeAnchor anchor) {
        String v = anchor.value() == null ? "" : anchor.value().strip();
        if (v.startsWith("at ")) {
            v = v.substring(3);
            int paren = v.indexOf('(');
            if (paren >= 0) {
                v = v.substring(0, paren);
            }
        }
        int dot = v.lastIndexOf('.');
        return dot >= 0 ? v.substring(dot + 1) : v;
    }

    /**
     * 读取符号行附近的源码片段。
     */
    private String readExcerpt(String workspacePath, String filePath, Integer lineNumber) {
        if (filePath == null) {
            return "";
        }
        try {
            int start = lineNumber == null ? 1 : Math.max(1, lineNumber);
            return workspace.readSource(Path.of(workspacePath), filePath, start, 20, 65536);
        } catch (WorkspaceException e) {
            return "";
        }
    }

    /**
     * 构造降级证据。
     */
    private CodeEvidence degraded(CodeSnapshot snapshot, String status) {
        return new CodeEvidence("code-degraded", null, null, null, null, snapshot.commitSha(), null, status);
    }
}
