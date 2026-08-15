package com.dpom.agent.web;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.common.codegraph.Symbol;
import com.dpom.agent.common.llm.ChatMessage;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.llm.ModelTurnResult;
import com.dpom.agent.common.logtemplate.LogParseResult;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 调查指标：提交数、终态数（低基数 status/resultType）、执行延迟；幂等重放不重复计数。
 */
@SpringBootTest
@AutoConfigureMockMvc
class InvestigationMetricsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MeterRegistry meterRegistry;

    @MockitoBean private ModelClient modelClient;
    @MockitoBean private CodeGraphClient codeGraphClient;
    @MockitoBean private LogTemplateMinerClient logTemplateMinerClient;

    @TempDir Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(workspace.resolve("AssetRepository.java"), "class AssetRepository { void insert(){} }");
        when(codeGraphClient.resolveSnapshot("asset-service", "abc1234")).thenReturn(
                new CodeSnapshot("s1", "asset-service", "abc1234", workspace.toString(), SnapshotStatus.READY));
        when(codeGraphClient.findSymbol(anyString(), anyString())).thenReturn(
                List.of(new Symbol("AssetRepository.insert", "method", "AssetRepository.java", 1)));
        when(logTemplateMinerClient.parseLogs(any())).thenAnswer(inv -> {
            List<String> lines = inv.getArgument(0);
            List<LogParseResult> results = new java.util.ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                results.add(new LogParseResult(i, 1, "device <*> insert failed", List.of()));
            }
            return results;
        });
        when(modelClient.complete(any())).thenReturn(new ModelTurnResult(ChatMessage.assistant(
                """
                {"type":"conclude","resultType":"ROOT_CAUSE_FOUND","rootCauseId":"AssetRepository.insert","rootCause":"r","summary":"s","evidenceIds":"ev-1,code-1"}
                """)));
    }

    @Test
    void submittedTerminatedAndDurationIncrement() throws Exception {
        double submittedBefore = counter("dpom.investigation.submitted");
        double terminatedBefore = counter("dpom.investigation.terminated", "status", "COMPLETED",
                "resultType", "ROOT_CAUSE_FOUND");
        double durationBefore = timer("dpom.investigation.execution.duration", "status", "COMPLETED",
                "resultType", "ROOT_CAUSE_FOUND", "errorCode", "NONE");

        long id = submit("metrics-" + UUID.randomUUID());
        pollUntil(id, "COMPLETED");

        assertThat(counter("dpom.investigation.submitted") - submittedBefore).isEqualTo(1.0);
        assertThat(counter("dpom.investigation.terminated", "status", "COMPLETED", "resultType", "ROOT_CAUSE_FOUND")
                - terminatedBefore).isEqualTo(1.0);
        assertThat(timer("dpom.investigation.execution.duration", "status", "COMPLETED", "resultType", "ROOT_CAUSE_FOUND",
                "errorCode", "NONE") - durationBefore).isEqualTo(1.0);
    }

    @Test
    void idempotentReplayDoesNotDoubleCount() throws Exception {
        String key = "metrics-replay-" + UUID.randomUUID();
        long id = submit(key);
        pollUntil(id, "COMPLETED");
        double submittedAfterFirst = counter("dpom.investigation.submitted");
        double terminatedAfterFirst = counter("dpom.investigation.terminated", "status", "COMPLETED",
                "resultType", "ROOT_CAUSE_FOUND");

        long replayId = submit(key);
        assertThat(replayId).isEqualTo(id);
        assertThat(counter("dpom.investigation.submitted")).isEqualTo(submittedAfterFirst);
        assertThat(counter("dpom.investigation.terminated", "status", "COMPLETED",
                "resultType", "ROOT_CAUSE_FOUND")).isEqualTo(terminatedAfterFirst);
    }

    private long submit(String key) throws Exception {
        String resp = mockMvc.perform(post("/api/v1/investigations").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(key))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("investigationId").asLong();
    }

    private void pollUntil(long id, String expected) throws Exception {
        String statusValue = "";
        for (int i = 0; i < 40 && !expected.equals(statusValue); i++) {
            Thread.sleep(250);
            statusValue = objectMapper.readTree(mockMvc.perform(get("/api/v1/investigations/" + id))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("status").asText();
        }
        assertThat(statusValue).isEqualTo(expected);
    }

    private double counter(String name, String... tags) {
        io.micrometer.core.instrument.Counter counter = search(name, tags).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private double timer(String name, String... tags) {
        io.micrometer.core.instrument.Timer timer = search(name, tags).timer();
        return timer == null ? 0.0 : timer.count();
    }

    private io.micrometer.core.instrument.search.Search search(String name, String... tags) {
        io.micrometer.core.instrument.search.Search result = meterRegistry.find(name);
        for (int i = 0; i < tags.length; i += 2) {
            result = result.tag(tags[i], tags[i + 1]);
        }
        return result;
    }

    private Map<String, Object> body(String key) {
        return Map.of("serviceCode", "asset-service", "environment", "prod", "release", "1.0.0",
                "commit", "abc1234", "symptom", "device rollback", "timeRange", "1h",
                "logs", List.of("ERROR com.example.asset.AssetRepository - device 1001 insert failed",
                        "ERROR com.example.asset.AssetRepository -     at com.example.asset.AssetRepository.insert(AssetRepository.java:42)"),
                "idempotencyKey", key);
    }
}
