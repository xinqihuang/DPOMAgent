package com.dpom.agent.web;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.common.codegraph.Symbol;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.logtemplate.LogParseResult;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.dpom.agent.core.persistence.command.ConclusionInsert;
import com.dpom.agent.core.investigation.InvestigationCoordinator;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.InvestigationDao;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 执行结果全路径：timer 每次执行尝试恰好 stop 一次，terminal counter 按路径语义一次；WAITING 暂停态受保护。
 */
@SpringBootTest
@AutoConfigureMockMvc
class InvestigationExecutionOutcomeTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private InvestigationDao investigationDao;
    @Autowired private ConclusionDao conclusionDao;

    @MockitoBean private ModelClient modelClient;
    @MockitoBean private CodeGraphClient codeGraphClient;
    @MockitoBean private LogTemplateMinerClient logTemplateMinerClient;
    @MockitoBean private InvestigationCoordinator coordinator;

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
    }

    @Test
    void completedRecordsTimerAndTerminalOnce() throws Exception {
        double timerBefore = timer("COMPLETED", "ROOT_CAUSE_FOUND", "NONE");
        double counterBefore = counter("COMPLETED", "ROOT_CAUSE_FOUND");
        coordinatorOutcome(InvestigationStatus.COMPLETED, "ROOT_CAUSE_FOUND", false);
        submitAndPoll("COMPLETED");
        assertThat(timer("COMPLETED", "ROOT_CAUSE_FOUND", "NONE") - timerBefore).isEqualTo(1.0);
        assertThat(counter("COMPLETED", "ROOT_CAUSE_FOUND") - counterBefore).isEqualTo(1.0);
    }

    @Test
    void inconclusiveRecordsTimerAndTerminalOnce() throws Exception {
        double timerBefore = timer("INCONCLUSIVE", "INCONCLUSIVE", "NONE");
        double counterBefore = counter("INCONCLUSIVE", "INCONCLUSIVE");
        coordinatorOutcome(InvestigationStatus.INCONCLUSIVE, "INCONCLUSIVE", false);
        submitAndPoll("INCONCLUSIVE");
        assertThat(timer("INCONCLUSIVE", "INCONCLUSIVE", "NONE") - timerBefore).isEqualTo(1.0);
        assertThat(counter("INCONCLUSIVE", "INCONCLUSIVE") - counterBefore).isEqualTo(1.0);
    }

    @Test
    void failedNormalReturnRecordsTimerAndTerminalOnce() throws Exception {
        double timerBefore = timer("FAILED", "FAILED", "NONE");
        double counterBefore = counter("FAILED", "FAILED");
        coordinatorOutcome(InvestigationStatus.FAILED, "FAILED", false);
        submitAndPoll("FAILED");
        assertThat(timer("FAILED", "FAILED", "NONE") - timerBefore).isEqualTo(1.0);
        assertThat(counter("FAILED", "FAILED") - counterBefore).isEqualTo(1.0);
    }

    @Test
    void waitingRecordsTimerOnly() throws Exception {
        double timerBefore = timer("WAITING_FOR_HUMAN", "NONE", "NONE");
        double counterBefore = counter("WAITING_FOR_HUMAN", "NONE");
        coordinatorOutcome(InvestigationStatus.WAITING_FOR_HUMAN, null, false);
        submitAndPoll("WAITING_FOR_HUMAN");
        assertThat(timer("WAITING_FOR_HUMAN", "NONE", "NONE") - timerBefore).isEqualTo(1.0);
        assertThat(counter("WAITING_FOR_HUMAN", "NONE") - counterBefore).isEqualTo(0.0);
    }

    @Test
    void exceptionRecordsTimerAndTerminalFailedOnce() throws Exception {
        double timerBefore = timer("FAILED", "FAILED", "EXECUTION_ERROR");
        double counterBefore = counter("FAILED", "FAILED");
        coordinatorThrowsWithoutSettingStatus();
        submitAndPoll("FAILED");
        assertThat(timer("FAILED", "FAILED", "EXECUTION_ERROR") - timerBefore).isEqualTo(1.0);
        assertThat(counter("FAILED", "FAILED") - counterBefore).isEqualTo(1.0);
    }

    @Test
    void exceptionAfterTerminalRecordsTimerOnceAndKeepsCompleted() throws Exception {
        double timerBefore = timer("COMPLETED", "ROOT_CAUSE_FOUND", "EXECUTION_ERROR");
        double completedCounterBefore = counter("COMPLETED", "ROOT_CAUSE_FOUND");
        double failedCounterBefore = counter("FAILED", "FAILED");
        coordinatorOutcome(InvestigationStatus.COMPLETED, "ROOT_CAUSE_FOUND", true);
        long id = submitAndPoll("COMPLETED");
        assertThat(investigationDao.findById(id).orElseThrow().status()).isEqualTo(InvestigationStatus.COMPLETED);
        assertThat(timer("COMPLETED", "ROOT_CAUSE_FOUND", "EXECUTION_ERROR") - timerBefore).isEqualTo(1.0);
        assertThat(counter("COMPLETED", "ROOT_CAUSE_FOUND") - completedCounterBefore).isEqualTo(1.0);
        assertThat(counter("FAILED", "FAILED") - failedCounterBefore).isEqualTo(0.0);
    }

    @Test
    void cancelledMapsStablyToFailed() throws Exception {
        double timerBefore = timer("FAILED", "FAILED", "NONE");
        double counterBefore = counter("FAILED", "FAILED");
        coordinatorOutcome(InvestigationStatus.CANCELLED, null, false);
        submitAndPoll("CANCELLED");
        assertThat(timer("FAILED", "FAILED", "NONE") - timerBefore).isEqualTo(1.0);
        assertThat(counter("FAILED", "FAILED") - counterBefore).isEqualTo(1.0);
    }

    @Test
    void waitingNotOverwrittenByException() throws Exception {
        double timerBefore = timer("WAITING_FOR_HUMAN", "NONE", "EXECUTION_ERROR");
        double failedCounterBefore = counter("FAILED", "FAILED");
        coordinatorOutcome(InvestigationStatus.WAITING_FOR_HUMAN, null, true);
        long id = submitAndPoll("WAITING_FOR_HUMAN");
        assertThat(investigationDao.findById(id).orElseThrow().status()).isEqualTo(InvestigationStatus.WAITING_FOR_HUMAN);
        assertThat(timer("WAITING_FOR_HUMAN", "NONE", "EXECUTION_ERROR") - timerBefore).isEqualTo(1.0);
        assertThat(counter("FAILED", "FAILED") - failedCounterBefore).isEqualTo(0.0);
    }

    private void coordinatorOutcome(InvestigationStatus status, String resultType, boolean throwAfter) {
        doAnswer(inv -> {
            long id = inv.getArgument(0);
            investigationDao.updateStatus(id, status);
            if (resultType != null) {
                ConclusionInsert conclusionCommand = new ConclusionInsert(id, resultType, null, null, null, null,
                        "s");
                conclusionDao.insert(conclusionCommand);
            }
            if (throwAfter) {
                throw new RuntimeException("after-terminal");
            }
            return null;
        }).when(coordinator).run(anyLong(), any(), any());
    }

    private void coordinatorThrowsWithoutSettingStatus() {
        doAnswer(inv -> { throw new RuntimeException("boom"); }).when(coordinator).run(anyLong(), any(), any());
    }

    private long submitAndPoll(String expected) throws Exception {
        String resp = mockMvc.perform(post("/api/v1/investigations").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("outcome-" + UUID.randomUUID()))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(resp).path("investigationId").asLong();
        String statusValue = "";
        for (int i = 0; i < 40 && !expected.equals(statusValue); i++) {
            Thread.sleep(250);
            statusValue = objectMapper.readTree(mockMvc.perform(get("/api/v1/investigations/" + id))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("status").asText();
        }
        assertThat(statusValue).isEqualTo(expected);
        return id;
    }

    private double timer(String status, String resultType, String errorCode) {
        io.micrometer.core.instrument.Timer timer = meterRegistry.find("dpom.investigation.execution.duration")
                .tag("status", status).tag("resultType", resultType).tag("errorCode", errorCode).timer();
        return timer == null ? 0.0 : timer.count();
    }

    private double counter(String status, String resultType) {
        io.micrometer.core.instrument.Counter counter = meterRegistry.find("dpom.investigation.terminated")
                .tag("status", status).tag("resultType", resultType).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private Map<String, Object> body(String key) {
        return Map.of("serviceCode", "asset-service", "environment", "prod", "release", "1.0.0",
                "commit", "abc1234", "symptom", "device rollback", "timeRange", "1h",
                "logs", List.of("ERROR com.example.asset.AssetRepository - device 1001 insert failed",
                        "ERROR com.example.asset.AssetRepository -     at com.example.asset.AssetRepository.insert(AssetRepository.java:42)"),
                "idempotencyKey", key);
    }
}
