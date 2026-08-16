package com.dpom.agent.core.logevidence;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeGraphException;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.common.codegraph.Symbol;
import com.dpom.agent.core.workspace.CodeWorkspace;
import com.dpom.agent.core.workspace.SearchHit;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T105 版本绑定图导航与源码解析验收。
 */
class CodeEvidenceResolverTest {

    private static final CodeAnchor ANCHOR =
            new CodeAnchor("CLASS_METHOD", "com.example.AssetRepository.insert", "ev-1", 0.9, "v1");

    private CodeSnapshot snapshot(SnapshotStatus status, String commit) {
        return new CodeSnapshot("s1", "asset-service", commit, "/repos/asset-service", status);
    }

    /**
     * READY 快照：CodeGraph 导航后读取源码，记录 commit/文件/行号。
     */
    @Test
    void resolvesReadySnapshotAndReadsSource() {
        CodeGraphClient cgc = mock(CodeGraphClient.class);
        when(cgc.findSymbol("s1", "insert"))
                .thenReturn(List.of(new Symbol("AssetRepository.insert", "method", "AssetRepository.java", 42)));
        CodeWorkspace ws = mock(CodeWorkspace.class);
        when(ws.readSource(any(Path.class), eq("AssetRepository.java"), eq(42), anyInt(), anyLong()))
                .thenReturn("throw new IllegalStateException()");
        CodeEvidenceResolver resolver = new CodeEvidenceResolver(cgc, ws);

        List<CodeEvidence> out = resolver.resolve("abc123", snapshot(SnapshotStatus.READY, "abc123"), List.of(ANCHOR));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).status()).isEqualTo("VERIFIED");
        assertThat(out.get(0).commit()).isEqualTo("abc123");
        assertThat(out.get(0).filePath()).isEqualTo("AssetRepository.java");
        assertThat(out.get(0).lineNumber()).isEqualTo(42);
        assertThat(out.get(0).excerpt()).contains("IllegalStateException");
    }

    /**
     * 非 READY 快照标记 NOT_READY，不读源码。
     */
    @Test
    void notReadySnapshotIsDegraded() {
        CodeEvidenceResolver resolver = new CodeEvidenceResolver(mock(CodeGraphClient.class), mock(CodeWorkspace.class));
        List<CodeEvidence> out = resolver.resolve("abc123", snapshot(SnapshotStatus.NOT_READY, "abc123"), List.of(ANCHOR));
        assertThat(out).extracting(CodeEvidence::status).containsExactly("NOT_READY");
    }

    /**
     * 提交不一致标记 VERSION_MISMATCH。
     */
    @Test
    void versionMismatchIsDegraded() {
        CodeEvidenceResolver resolver = new CodeEvidenceResolver(mock(CodeGraphClient.class), mock(CodeWorkspace.class));
        List<CodeEvidence> out = resolver.resolve("abc123", snapshot(SnapshotStatus.READY, "xyz789"), List.of(ANCHOR));
        assertThat(out).extracting(CodeEvidence::status).containsExactly("VERSION_MISMATCH");
    }

    /**
     * 图不可用时降级为工作区文本搜索。
     */
    @Test
    void graphUnavailableFallsBackToWorkspace() {
        CodeGraphClient cgc = mock(CodeGraphClient.class);
        when(cgc.findSymbol(anyString(), anyString())).thenThrow(new CodeGraphException("unavailable"));
        CodeWorkspace ws = mock(CodeWorkspace.class);
        when(ws.searchText(any(Path.class), eq("com.example.AssetRepository.insert"), anyInt()))
                .thenReturn(List.of(new SearchHit("AssetRepository.java", 42, "insert")));
        CodeEvidenceResolver resolver = new CodeEvidenceResolver(cgc, ws);

        List<CodeEvidence> out = resolver.resolve("abc123", snapshot(SnapshotStatus.READY, "abc123"), List.of(ANCHOR));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).status()).isEqualTo("WORKSPACE_FALLBACK");
        assertThat(out.get(0).filePath()).isEqualTo("AssetRepository.java");
    }
}
