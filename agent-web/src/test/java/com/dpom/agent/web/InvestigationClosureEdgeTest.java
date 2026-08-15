package com.dpom.agent.web;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.common.codegraph.Symbol;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.logtemplate.LogParseResult;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.investigation.InvestigationCoordinator;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.persistence.ApiRequestRecord;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.InvestigationApiRequestDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.web.metrics.InvestigationMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 收口边界：updateRunning 异常走统一补偿 + 恰一次 timer + MDC 清理；metrics 记录阶段异常不破坏 api_request 收口与 MDC 清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InvestigationClosureEdgeTest.MdcCaptureConfig.class)
class InvestigationClosureEdgeTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private InvestigationDao investigationDao;
    @Autowired private ConclusionDao conclusionDao;

    @MockitoBean private ModelClient modelClient;
    @MockitoBean private CodeGraphClient codeGraphClient;
    @MockitoBean private LogTemplateMinerClient logTemplateMinerClient;
    @MockitoBean private InvestigationCoordinator coordinator;
    @MockitoSpyBean private InvestigationApiRequestDao apiRequestDao;
    @MockitoSpyBean private InvestigationMetrics metrics;

    @TempDir Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        MdcCaptureConfig.AFTER_TASK_MDC.set("unset");
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
    void updateRunningFailureCompensatesAndCleansMdc() throws Exception {
        doThrow(new RuntimeException("db-boom")).when(apiRequestDao).updateRunning(anyLong());
        double timerBefore = timer("FAILED", "FAILED", "EXECUTION_ERROR");
        double counterBefore = counter("FAILED", "FAILED");

        long id = submit("upd-" + UUID.randomUUID());
        pollUntil(id, "FAILED");

        assertThat(investigationDao.findById(id).orElseThrow().status()).isEqualTo(InvestigationStatus.FAILED);
        assertThat(timer("FAILED", "FAILED", "EXECUTION_ERROR") - timerBefore).isEqualTo(1.0);
        assertThat(counter("FAILED", "FAILED") - counterBefore).isEqualTo(1.0);
        assertThat(MdcCaptureConfig.AFTER_TASK_MDC.get()).isNull();
    }

    @Test
    void metricsRecordingFailureDoesNotBreakClosure() throws Exception {
        doAnswer(inv -> {
            long investigationId = inv.getArgument(0);
            investigationDao.updateStatus(investigationId, InvestigationStatus.COMPLETED);
            conclusionDao.insert(new Conclusion(null, investigationId, "ROOT_CAUSE_FOUND", null, null, null, null,
                    "s", null));
            return null;
        }).when(coordinator).run(anyLong(), any(), any());
        doThrow(new RuntimeException("metrics-boom")).when(metrics).stopExecution(any(), anyString(), anyString(),
                anyString());

        long id = submit("met-" + UUID.randomUUID());
        pollUntil(id, "COMPLETED");

        assertThat(investigationDao.findById(id).orElseThrow().status()).isEqualTo(InvestigationStatus.COMPLETED);
        ApiRequestRecord record = apiRequestDao.findByInvestigationId(id).orElseThrow();
        assertThat(record.status()).isEqualTo("COMPLETED");
        assertThat(MdcCaptureConfig.AFTER_TASK_MDC.get()).isNull();
    }

    @Test
    void recordSubmittedFailureDoesNotBlockDispatch() throws Exception {
        doThrow(new RuntimeException("metrics-boom")).when(metrics).recordSubmitted();
        coordinatorCompleted();
        long id = submit("submitted-" + UUID.randomUUID());
        pollUntil(id, "COMPLETED");
        assertThat(investigationDao.findById(id).orElseThrow().status()).isEqualTo(InvestigationStatus.COMPLETED);
        assertThat(apiRequestDao.findByInvestigationId(id).orElseThrow().status()).isEqualTo("COMPLETED");
    }

    @Test
    void startExecutionFailureDoesNotBreakBusiness() throws Exception {
        doThrow(new RuntimeException("metrics-boom")).when(metrics).startExecution();
        coordinatorCompleted();
        long id = submit("start-" + UUID.randomUUID());
        pollUntil(id, "COMPLETED");
        assertThat(investigationDao.findById(id).orElseThrow().status()).isEqualTo(InvestigationStatus.COMPLETED);
        assertThat(apiRequestDao.findByInvestigationId(id).orElseThrow().status()).isEqualTo("COMPLETED");
        assertThat(MdcCaptureConfig.AFTER_TASK_MDC.get()).isNull();
    }

    @Test
    void recordTerminatedFailureDoesNotOverrideRejection() throws Exception {
        doThrow(new RuntimeException("metrics-boom")).when(metrics).recordTerminated("REJECTED", "REJECTED");
        CountDownLatch block = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(14);
        doAnswer(inv -> {
            try {
                block.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
            long investigationId = inv.getArgument(0);
            investigationDao.updateStatus(investigationId, InvestigationStatus.COMPLETED);
            conclusionDao.insert(new Conclusion(null, investigationId, "ROOT_CAUSE_FOUND", null, null, null, null,
                    "s", null));
            return null;
        }).when(coordinator).run(anyLong(), any(), any());

        for (int i = 0; i < 14; i++) {
            submit("fill-" + UUID.randomUUID());
        }
        String err = mockMvc.perform(post("/api/v1/investigations").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("reject-" + UUID.randomUUID()))))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();
        assertThat(err).contains("CAPACITY_FULL");
        block.countDown();
        done.await(30, TimeUnit.SECONDS);
    }

    private void coordinatorCompleted() {
        doAnswer(inv -> {
            long investigationId = inv.getArgument(0);
            investigationDao.updateStatus(investigationId, InvestigationStatus.COMPLETED);
            conclusionDao.insert(new Conclusion(null, investigationId, "ROOT_CAUSE_FOUND", null, null, null, null,
                    "s", null));
            return null;
        }).when(coordinator).run(anyLong(), any(), any());
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

    @TestConfiguration
    static class MdcCaptureConfig {

        static final AtomicReference<String> AFTER_TASK_MDC = new AtomicReference<>();

        @Bean
        static BeanPostProcessor mdcCapture() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof ThreadPoolTaskExecutor executor && "investigationExecutor".equals(beanName)) {
                        executor.setTaskDecorator(task -> () -> {
                            try {
                                task.run();
                            } finally {
                                AFTER_TASK_MDC.set(MDC.get("correlationId"));
                            }
                        });
                    }
                    return bean;
                }
            };
        }
    }
}
