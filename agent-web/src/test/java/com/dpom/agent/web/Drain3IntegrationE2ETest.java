package com.dpom.agent.web;

import com.dpom.agent.adapter.runtime.McpLogTemplateMinerClient;
import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.dpom.agent.core.logevidence.EvidenceBundleBuilder;
import com.dpom.agent.core.logevidence.LogEvidence;
import com.dpom.agent.core.logevidence.LogEvidenceService;
import com.dpom.agent.core.workspace.CodeWorkspace;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T109 真实 Drain3 集成 E2E：相似日志同一模板、参数抽取、无原始 secret、机器可读结果。
 *
 * <p>由 DPOM_E2E_DRAIN3=true 显式启用，默认跳过。</p>
 */
@EnabledIfEnvironmentVariable(named = "DPOM_E2E_DRAIN3", matches = "true")
class Drain3IntegrationE2ETest {

    /**
     * 真实 Drain3 把变量不同的相似日志聚为同一模板并抽取参数，且不写原始 secret。
     */
    @Test
    void clustersSimilarLogsIntoSameTemplate() throws Exception {
        LogTemplateMinerClient miner = new McpLogTemplateMinerClient(() ->
                McpClient.sync(HttpClientSseClientTransport.builder("http://localhost:8100").build()).build());
        CodeGraphClient cgc = mock(CodeGraphClient.class);
        when(cgc.findSymbol(anyString(), anyString())).thenReturn(List.of());
        LogEvidenceService service = new LogEvidenceService(miner, cgc, mock(CodeWorkspace.class),
                new EvidenceBundleBuilder(1_000_000));
        CodeSnapshot snapshot = new CodeSnapshot("s1", "asset-service", "c", "/x", SnapshotStatus.READY);

        List<String> logs = List.of(
                "ERROR svc - device 1001 insert failed password=secret123",
                "ERROR svc - device 1002 insert failed",
                "ERROR svc - device 1003 insert failed",
                "ERROR svc - device 9999 insert failed");

        EvidenceBundle bundle = service.run("asset-service", "prod", "1.0.0", "c", "1h", "drain3-mcp-0.9", snapshot,
                logs);

        assertThat(bundle.logEvidences()).isNotEmpty();
        LogEvidence evidence = bundle.logEvidences().stream()
                .filter(e -> e.summary().template().contains("device")).findFirst().orElseThrow();
        assertThat(evidence.summary().clusterId()).isPositive();
        assertThat(evidence.summary().template()).isNotBlank().contains("<");
        assertThat(evidence.summary().parameterDistribution().valuesByMask()).isNotEmpty();
        assertThat(evidence.summary().parameterDistribution().valuesByMask().values()).anyMatch(v -> v.size() >= 2);
        for (LogEvidence e : bundle.logEvidences()) {
            for (String sample : e.summary().representativeSamples()) {
                assertThat(sample).doesNotContain("secret123");
            }
        }
        writeResult(evidence);
    }

    /**
     * 写机器可读结果。
     */
    private void writeResult(LogEvidence evidence) throws Exception {
        Path out = Path.of("target", "e2e-results", "drain3-e2e.json").toAbsolutePath().normalize();
        Files.deleteIfExists(out);
        Files.createDirectories(out.getParent());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executed", true);
        result.put("passed", true);
        result.put("minerVersion", "drain3-mcp-0.9");
        result.put("clusterIds", List.of(evidence.summary().clusterId()));
        result.put("template", evidence.summary().template());
        result.put("parameterCount", evidence.summary().parameterDistribution().valuesByMask().size());
        result.put("timestamp", Instant.now().toString());
        Path tmp = out.resolveSibling("drain3-e2e.json.tmp");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), result);
        Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        assertThat(Files.exists(out)).isTrue();
    }
}
