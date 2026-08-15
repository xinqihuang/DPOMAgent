package com.dpom.agent.core.logevidence;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.common.codegraph.Symbol;
import com.dpom.agent.common.logtemplate.LogParameter;
import com.dpom.agent.common.logtemplate.LogParseResult;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.dpom.agent.core.workspace.CodeWorkspace;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T106 日志证据服务集成验收：编排完整管道产出证据束。
 */
class LogEvidenceServiceTest {

    private static final CodeSnapshot SNAPSHOT =
            new CodeSnapshot("s1", "asset-service", "abc123", "/repos/asset-service", SnapshotStatus.READY);

    /**
     * 跑通完整管道：日志 -> 模板 -> 锚点 -> 源码证据 -> 证据束。
     */
    @Test
    void producesEvidenceBundle() {
        LogTemplateMinerClient miner = mock(LogTemplateMinerClient.class);
        when(miner.parseLogs(any())).thenReturn(List.of(
                new LogParseResult(7, 1, "device <*> insert failed", List.of(new LogParameter("1", "deviceId")))));
        CodeGraphClient cgc = mock(CodeGraphClient.class);
        when(cgc.findSymbol("s1", "insert"))
                .thenReturn(List.of(new Symbol("AssetRepository.insert", "method", "AssetRepository.java", 42)));
        CodeWorkspace ws = mock(CodeWorkspace.class);
        when(ws.readSource(any(Path.class), eq("AssetRepository.java"), eq(42), anyInt(), anyLong()))
                .thenReturn("throw new IllegalStateException()");
        LogEvidenceService service = new LogEvidenceService(miner, cgc, ws, new EvidenceBundleBuilder(100_000));

        EvidenceBundle bundle = service.run("asset-service", "prod", "1.0.0", "abc123", "1h", "drain3-0.9",
                SNAPSHOT, List.of("ERROR com.example.AssetRepository.insert - device 1 insert failed"));

        assertThat(bundle.logEvidences()).hasSize(1);
        assertThat(bundle.logEvidences().get(0).summary().template()).isEqualTo("device <*> insert failed");
        assertThat(bundle.codeEvidences()).hasSize(1);
        assertThat(bundle.codeEvidences().get(0).status()).isEqualTo("VERIFIED");
        assertThat(bundle.hasVerifiedSource()).isTrue();
    }

    /**
     * Drain3 不可用时降级并记录 LOG_MINER_UNAVAILABLE，仍产出有界日志证据。
     */
    /**
     * 脱敏先于 Drain3：捕获传给 parseLogs 的参数，不得包含原始 secret。
     */
    @Test
    void redactsBeforeSendingToMiner() {
        LogTemplateMinerClient miner = mock(LogTemplateMinerClient.class);
        AtomicReference<List<String>> sent = new AtomicReference<>();
        when(miner.parseLogs(any())).thenAnswer(inv -> {
            sent.set(inv.getArgument(0));
            return List.of(new LogParseResult(1, 1, "x", List.of()));
        });
        CodeGraphClient cgc = mock(CodeGraphClient.class);
        when(cgc.findSymbol(anyString(), anyString())).thenReturn(List.of());
        LogEvidenceService service = new LogEvidenceService(miner, cgc, mock(CodeWorkspace.class),
                new EvidenceBundleBuilder(100_000));

        service.run("s", "prod", "1.0.0", "c", "1h", "drain3-0.9", SNAPSHOT,
                List.of("ERROR svc - password=secret123 token=abc Authorization: Bearer xyz"));

        String sentMsg = String.join("\n", sent.get());
        assertThat(sentMsg).doesNotContain("secret123").doesNotContain("abc").doesNotContain("xyz");
        assertThat(sentMsg).contains("h:");
    }

    @Test
    void degradesWhenMinerUnavailable() {
        LogTemplateMinerClient miner = mock(LogTemplateMinerClient.class);
        when(miner.parseLogs(any())).thenThrow(new RuntimeException("drain3 down"));
        LogEvidenceService service = new LogEvidenceService(miner, mock(CodeGraphClient.class), mock(CodeWorkspace.class),
                new EvidenceBundleBuilder(100_000));

        EvidenceBundle bundle = service.run("s", "prod", "1.0.0", "c", "1h", "drain3-0.9", SNAPSHOT,
                List.of("ERROR device 1 insert failed"));

        assertThat(bundle.degradations()).contains("LOG_MINER_UNAVAILABLE");
        assertThat(bundle.logEvidences()).hasSize(1);
    }
}
