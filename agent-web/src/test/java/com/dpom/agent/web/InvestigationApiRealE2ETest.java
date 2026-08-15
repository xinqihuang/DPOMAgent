package com.dpom.agent.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实外部 HTTP E2E：通过 HTTP API 提交 E01，真实 Drain3/CGC/DeepSeek。由 DPOM_E2E_FULL=true 显式启用。
 */
@EnabledIfEnvironmentVariable(named = "DPOM_E2E_FULL", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc
class InvestigationApiRealE2ETest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void submitE01ViaHttp() throws Exception {
        Path out = Path.of("target", "e2e-results", "investigation-api-e2e.json").toAbsolutePath().normalize();
        Files.deleteIfExists(out);

        Map<String, Object> body = Map.of("serviceCode", "asset-service", "environment", "prod", "release", "1.0.0",
                "commit", "e01abc", "symptom", "device create transaction rollback", "timeRange", "1h",
                "logs", List.of("ERROR com.example.asset.AssetRepository - device 1001 insert failed",
                        "ERROR com.example.asset.AssetRepository - java.lang.IllegalStateException: insert failed",
                        "ERROR com.example.asset.AssetRepository -     at com.example.asset.AssetRepository.insert(AssetRepository.java:42)"),
                "idempotencyKey", "e2e-real-1");

        String resp = mockMvc.perform(post("/api/v1/investigations").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(resp).path("investigationId").asLong();

        String statusValue = "";
        for (int i = 0; i < 120 && !"COMPLETED".equals(statusValue) && !"INCONCLUSIVE".equals(statusValue); i++) {
            Thread.sleep(1000);
            statusValue = objectMapper.readTree(mockMvc.perform(get("/api/v1/investigations/" + id))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("status").asText();
        }

        String conclusion = mockMvc.perform(get("/api/v1/investigations/" + id + "/conclusion")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String rootCauseId = objectMapper.readTree(conclusion).path("rootCauseId").asText();

        boolean passed = "COMPLETED".equals(statusValue) && "AssetRepository.insert".equals(rootCauseId);
        assertThat(passed).as("E01 真实 HTTP E2E").isTrue();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executed", true);
        result.put("passed", passed);
        result.put("caseId", "E01");
        result.put("investigationId", id);
        result.put("status", statusValue);
        result.put("rootCauseId", rootCauseId);
        result.put("model", "deepseek-v4-pro");
        result.put("timestamp", Instant.now().toString());
        Files.createDirectories(out.getParent());
        Path tmp = out.resolveSibling("investigation-api-e2e.json.tmp");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), result);
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
