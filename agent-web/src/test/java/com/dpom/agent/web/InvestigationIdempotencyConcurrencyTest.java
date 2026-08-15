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
import com.dpom.agent.web.dto.InvestigationSubmitRequest;
import com.dpom.agent.web.service.InvestigationApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 幂等并发：DB 唯一约束为最终仲裁，同 key 同 payload 一个 id，同 key 异 payload 一个成功其余 409。
 */
@SpringBootTest
class InvestigationIdempotencyConcurrencyTest {

    @Autowired private InvestigationApplicationService service;

    @MockitoBean private ModelClient modelClient;
    @MockitoBean private CodeGraphClient codeGraphClient;
    @MockitoBean private LogTemplateMinerClient logTemplateMinerClient;

    @TempDir Path workspace;

    private CountDownLatch done;

    @BeforeEach
    void setUp() throws Exception {
        done = new CountDownLatch(1);
        Files.writeString(workspace.resolve("AssetRepository.java"), "class AssetRepository { void insert(){} }");
        when(codeGraphClient.resolveSnapshot("asset-service", "abc1234")).thenReturn(
                new CodeSnapshot("s1", "asset-service", "abc1234", workspace.toString(), SnapshotStatus.READY));
        when(codeGraphClient.findSymbol(anyString(), anyString())).thenReturn(
                List.of(new Symbol("AssetRepository.insert", "method", "AssetRepository.java", 1)));
        when(logTemplateMinerClient.parseLogs(any())).thenAnswer(inv -> {
            List<String> lines = inv.getArgument(0);
            List<LogParseResult> results = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                results.add(new LogParseResult(i, 1, "device <*> insert failed", List.of()));
            }
            return results;
        });
        when(modelClient.complete(any())).thenAnswer(inv -> {
            done.countDown();
            return new ModelTurnResult(ChatMessage.assistant(
                    """
                    {"type":"conclude","resultType":"ROOT_CAUSE_FOUND","rootCauseId":"AssetRepository.insert","rootCause":"r","summary":"s","evidenceIds":"ev-1,code-1"}
                    """));
        });
    }

    @Test
    void concurrentSameKeySamePayloadReturnsOneId() throws Exception {
        String key = "conc-same-" + UUID.randomUUID();
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return service.submit(req(key, "device rollback"));
                }));
            }
            start.countDown();
            Set<Long> ids = new HashSet<>();
            for (Future<Long> future : futures) {
                ids.add(future.get());
            }
            assertThat(ids).hasSize(1);
            assertThat(ids.iterator().next()).isPositive();
            done.await(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentSameKeyDifferentPayloadYieldsOneSuccessAndRestConflict() throws Exception {
        String key = "conc-diff-" + UUID.randomUUID();
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return (Object) service.submit(req(key, "device rollback " + idx));
                    } catch (ResponseStatusException e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            int success = 0;
            int conflict = 0;
            for (Future<Object> future : futures) {
                Object result = future.get();
                if (result instanceof Long) {
                    success++;
                } else if (result instanceof ResponseStatusException e && e.getStatusCode().value() == 409) {
                    conflict++;
                }
            }
            assertThat(success).isEqualTo(1);
            assertThat(conflict).isEqualTo(threads - 1);
            done.await(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private InvestigationSubmitRequest req(String key, String symptom) {
        return new InvestigationSubmitRequest("asset-service", "prod", "1.0.0", "abc1234", symptom, "1h",
                List.of("ERROR com.example.asset.AssetRepository - device 1001 insert failed",
                        "ERROR com.example.asset.AssetRepository -     at com.example.asset.AssetRepository.insert(AssetRepository.java:42)"),
                key);
    }
}
