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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP 层端到端：通过真实 Spring HTTP 入口提交/轮询/读证据与结论，外部适配器用 mock 替换。
 */
@SpringBootTest
@AutoConfigureMockMvc
class InvestigationApiHttpE2ETest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

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
        when(logTemplateMinerClient.parseLogs(any())).thenAnswer(inv -> parseResults(inv.getArgument(0)));
        when(modelClient.complete(any())).thenReturn(new ModelTurnResult(ChatMessage.assistant(concludeJson())));
    }

    @Test
    void submitPollAndReadEvidenceAndConclusion() throws Exception {
        String key = "key-1";
        String resp = mockMvc.perform(post("/api/v1/investigations").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(key, "device rollback"))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(resp).path("investigationId").asLong();
        assertThat(id).isPositive();

        String statusValue = "";
        for (int i = 0; i < 40 && !"COMPLETED".equals(statusValue); i++) {
            Thread.sleep(500);
            String s = mockMvc.perform(get("/api/v1/investigations/" + id)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            statusValue = objectMapper.readTree(s).path("status").asText();
        }
        assertThat(statusValue).isEqualTo("COMPLETED");

        String evidence = mockMvc.perform(get("/api/v1/investigations/" + id + "/evidence")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(evidence).contains("\"available\":true").contains("\"evidenceId\":\"ev-1\"")
                .contains("\"status\":\"VERIFIED\"");
        String conclusion = mockMvc.perform(get("/api/v1/investigations/" + id + "/conclusion")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(conclusion).contains("ROOT_CAUSE_FOUND").contains("AssetRepository.insert");
        assertThat(conclusion).doesNotContain("secret");
    }

    @Test
    void invalidRequestReturnsBadRequest() throws Exception {
        Map<String, Object> body = Map.of("serviceCode", "bad_service!", "environment", "prod", "release", "1.0.0",
                "commit", "abc1234", "symptom", "s", "timeRange", "1h", "logs", List.of("x"));
        String err = mockMvc.perform(post("/api/v1/investigations").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(err).contains("BAD_REQUEST").doesNotContain("Exception", "at ", "java.");
    }

    @Test
    void missingInvestigationReturnsNotFound() throws Exception {
        String err = mockMvc.perform(get("/api/v1/investigations/999999")).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(err).contains("NOT_FOUND").doesNotContain("Exception", "at ", "java.");
    }

    @Test
    void idempotentReplayReturnsSameId() throws Exception {
        Map<String, Object> body = body("key-replay", "device rollback");
        String json = objectMapper.writeValueAsString(body);
        long first = objectMapper.readTree(mockMvc.perform(post("/api/v1/investigations")
                        .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString()).path("investigationId").asLong();
        long second = objectMapper.readTree(mockMvc.perform(post("/api/v1/investigations")
                        .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString()).path("investigationId").asLong();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void idempotentConflictReturns409WithInvestigationId() throws Exception {
        String key = "key-conflict-" + UUID.randomUUID();
        long firstId = objectMapper.readTree(mockMvc.perform(post("/api/v1/investigations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(key, "device rollback"))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString())
                .path("investigationId").asLong();
        String err = mockMvc.perform(post("/api/v1/investigations").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(key, "different symptom"))))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(err).contains("IDEMPOTENCY_CONFLICT").contains(String.valueOf(firstId))
                .doesNotContain("Exception", "at ", "java.");
    }

    @Test
    void conclusionAndEvidenceNotReadyReturn200AvailableFalse() throws Exception {
        CountDownLatch block = new CountDownLatch(1);
        doAnswer(inv -> {
            block.await(30, TimeUnit.SECONDS);
            return parseResults(inv.getArgument(0));
        }).when(logTemplateMinerClient).parseLogs(any());
        String key = "notready-" + UUID.randomUUID();
        long id = objectMapper.readTree(mockMvc.perform(post("/api/v1/investigations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(key, "device rollback"))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString())
                .path("investigationId").asLong();
        String conclusion = mockMvc.perform(get("/api/v1/investigations/" + id + "/conclusion"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(conclusion).contains("\"available\":false");
        String evidence = mockMvc.perform(get("/api/v1/investigations/" + id + "/evidence"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(evidence).contains("\"available\":false");
        block.countDown();
        String statusValue = "";
        for (int i = 0; i < 40 && !statusValue.equals("COMPLETED") && !statusValue.equals("FAILED"); i++) {
            Thread.sleep(250);
            statusValue = objectMapper.readTree(mockMvc.perform(get("/api/v1/investigations/" + id))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("status").asText();
        }
    }

    @Test
    void healthReportsUp() throws Exception {
        String body = mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"status\":\"UP\"").contains("\"db\":\"UP\"")
                .contains("\"available\":true");
    }

    private Map<String, Object> body(String key, String symptom) {
        return Map.of("serviceCode", "asset-service", "environment", "prod", "release", "1.0.0",
                "commit", "abc1234", "symptom", symptom, "timeRange", "1h",
                "logs", List.of("ERROR com.example.asset.AssetRepository - device 1001 insert failed",
                        "ERROR com.example.asset.AssetRepository -     at com.example.asset.AssetRepository.insert(AssetRepository.java:42)"),
                "idempotencyKey", key);
    }

    private List<LogParseResult> parseResults(List<String> lines) {
        List<LogParseResult> results = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            results.add(new LogParseResult(i, 1, "device <*> insert failed", List.of()));
        }
        return results;
    }

    private String concludeJson() {
        return """
                {"type":"conclude","resultType":"ROOT_CAUSE_FOUND","rootCauseId":"AssetRepository.insert","rootCause":"r","summary":"s","evidenceIds":"ev-1,code-1"}
                """;
    }
}
