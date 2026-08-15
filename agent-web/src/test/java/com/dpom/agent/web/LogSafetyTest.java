package com.dpom.agent.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.common.codegraph.Symbol;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.logtemplate.LogParseResult;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.persistence.ApiRequestRecord;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.InvestigationApiRequestDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.web.service.InvestigationApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 日志安全：异常 message 含 secret/raw-log/SQL/path sentinel 时，日志全文不得包含这些 sentinel 与堆栈；API 仍稳定。
 */
@SpringBootTest
@AutoConfigureMockMvc
class LogSafetyTest {

    private static final String SECRET_SENTINEL = "SECRET_SENTINEL";
    private static final String SECRET_VALUE = "hunter2-secret-value";
    private static final String RAW_LOG_SENTINEL = "raw-log-sentinel-line";
    private static final String SQL_SENTINEL = "SELECT * FROM secret_users WHERE token = 'x'";
    private static final String PATH_SENTINEL = "C:\\secret\\absolute\\path\\credentials.txt";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InvestigationDao investigationDao;
    @Autowired private ConclusionDao conclusionDao;
    @Autowired private InvestigationApiRequestDao apiRequestDao;

    @MockitoBean private ModelClient modelClient;
    @MockitoBean private CodeGraphClient codeGraphClient;
    @MockitoBean private LogTemplateMinerClient logTemplateMinerClient;

    @TempDir Path workspace;

    private ListAppender<ILoggingEvent> appender;

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
        when(modelClient.complete(any())).thenThrow(new RuntimeException(
                SECRET_SENTINEL + " " + SECRET_VALUE + " " + RAW_LOG_SENTINEL + " " + SQL_SENTINEL + " "
                        + PATH_SENTINEL));
        appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(InvestigationApplicationService.class)).addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(InvestigationApplicationService.class)).detachAppender(appender);
    }

    @Test
    void asyncFailureLogsNoSecretRawLogSqlPathOrStacktrace() throws Exception {
        String key = "logsafe-" + UUID.randomUUID();
        String resp = mockMvc.perform(post("/api/v1/investigations").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(key))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(resp).path("investigationId").asLong();

        String statusValue = "";
        for (int i = 0; i < 40 && !"FAILED".equals(statusValue); i++) {
            Thread.sleep(250);
            statusValue = objectMapper.readTree(mockMvc.perform(get("/api/v1/investigations/" + id))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("status").asText();
        }
        assertThat(statusValue).isEqualTo("FAILED");

        assertThat(investigationDao.findById(id).orElseThrow().status()).isEqualTo(InvestigationStatus.FAILED);
        Conclusion conclusion = conclusionDao.findByInvestigationId(id).orElseThrow();
        assertThat(conclusion.resultType()).isEqualTo("FAILED");
        assertThat(conclusion.summary()).isEqualTo("执行失败");
        ApiRequestRecord record = apiRequestDao.findByInvestigationId(id).orElseThrow();
        assertThat(record.status()).isEqualTo("FAILED");
        assertThat(record.lastErrorCode()).isEqualTo("EXECUTION_ERROR");

        String all = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertThat(all).contains("investigationId=").contains("errorCode=EXECUTION_ERROR");
        assertThat(all).doesNotContain(SECRET_SENTINEL, SECRET_VALUE, RAW_LOG_SENTINEL, "SELECT * FROM secret_users",
                PATH_SENTINEL, "at com.dpom", "RuntimeException");
        assertThat(appender.list).allMatch(event -> event.getThrowableProxy() == null);
        assertThat(appender.list).isNotEmpty();
        assertThat(appender.list).allMatch(event -> event.getMDCPropertyMap().containsKey("correlationId"));
    }

    private Map<String, Object> body(String key) {
        return Map.of("serviceCode", "asset-service", "environment", "prod", "release", "1.0.0",
                "commit", "abc1234", "symptom", "device rollback", "timeRange", "1h",
                "logs", List.of("ERROR com.example.asset.AssetRepository - device 1001 insert failed",
                        "ERROR com.example.asset.AssetRepository -     at com.example.asset.AssetRepository.insert(AssetRepository.java:42)"),
                "idempotencyKey", key);
    }
}
