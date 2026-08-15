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
import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.persistence.ApiRequestRecord;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.InvestigationApiRequestDao;
import com.dpom.agent.core.persistence.InvestigationDao;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 队列满拒绝：15 个提交（4 活动 + 10 队列 + 1 拒绝）拒绝时事务化补偿，不留 CREATED 孤儿。
 */
@SpringBootTest
@AutoConfigureMockMvc
class InvestigationRejectionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InvestigationDao investigationDao;
    @Autowired private ConclusionDao conclusionDao;
    @Autowired private InvestigationApiRequestDao apiRequestDao;

    @MockitoBean private ModelClient modelClient;
    @MockitoBean private CodeGraphClient codeGraphClient;
    @MockitoBean private LogTemplateMinerClient logTemplateMinerClient;

    @TempDir Path workspace;

    private CountDownLatch block;
    private CountDownLatch done;

    @BeforeEach
    void setUp() throws Exception {
        block = new CountDownLatch(1);
        done = new CountDownLatch(14);
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
        when(modelClient.complete(any())).thenAnswer(inv -> {
            block.await(30, TimeUnit.SECONDS);
            done.countDown();
            return new ModelTurnResult(ChatMessage.assistant(
                    """
                    {"type":"conclude","resultType":"ROOT_CAUSE_FOUND","rootCauseId":"AssetRepository.insert","rootCause":"r","summary":"s","evidenceIds":"ev-1,code-1"}
                    """));
        });
    }

    @Test
    void queueFullRejectsAndCompensates() throws Exception {
        for (int i = 0; i < 14; i++) {
            mockMvc.perform(post("/api/v1/investigations").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body("fill-" + i + "-" + UUID.randomUUID()))))
                    .andExpect(status().isAccepted());
        }

        String rejectedKey = "reject-" + UUID.randomUUID();
        String err = mockMvc.perform(post("/api/v1/investigations").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(rejectedKey))))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();
        assertThat(err).contains("CAPACITY_FULL").doesNotContain("Exception", "at ", "java.");

        block.countDown();
        done.await(30, TimeUnit.SECONDS);

        ApiRequestRecord record = apiRequestDao.findByIdempotencyKey(rejectedKey).orElseThrow();
        assertThat(record.status()).isEqualTo("REJECTED");
        assertThat(record.lastErrorCode()).isEqualTo("CAPACITY_FULL");
        long rejectedId = record.investigationId();
        Investigation inv = investigationDao.findById(rejectedId).orElseThrow();
        assertThat(inv.status()).isEqualTo(InvestigationStatus.FAILED);
        Conclusion conclusion = conclusionDao.findByInvestigationId(rejectedId).orElseThrow();
        assertThat(conclusion.resultType()).isEqualTo("REJECTED");
        assertThat(conclusion.summary()).contains("队列已满");

        String replay = mockMvc.perform(post("/api/v1/investigations").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(rejectedKey))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(replay).path("investigationId").asLong()).isEqualTo(rejectedId);
        assertThat(objectMapper.readTree(replay).path("status").asText()).isEqualTo("FAILED");
    }

    private Map<String, Object> body(String key) {
        return Map.of("serviceCode", "asset-service", "environment", "prod", "release", "1.0.0",
                "commit", "abc1234", "symptom", "device rollback", "timeRange", "1h",
                "logs", List.of("ERROR com.example.asset.AssetRepository - device 1001 insert failed",
                        "ERROR com.example.asset.AssetRepository -     at com.example.asset.AssetRepository.insert(AssetRepository.java:42)"),
                "idempotencyKey", key);
    }
}
