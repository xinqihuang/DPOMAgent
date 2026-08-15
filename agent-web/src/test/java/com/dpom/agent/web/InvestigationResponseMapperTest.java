package com.dpom.agent.web;

import com.dpom.agent.core.logevidence.CodeAnchor;
import com.dpom.agent.core.logevidence.CodeEvidence;
import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.dpom.agent.core.logevidence.EvidenceProvenance;
import com.dpom.agent.core.logevidence.LogEvidence;
import com.dpom.agent.core.logevidence.LogTemplateSummary;
import com.dpom.agent.core.logevidence.ParameterDistribution;
import com.dpom.agent.web.dto.EvidenceResponse;
import com.dpom.agent.web.dto.InvestigationResponseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DTO 隔离与 JSON schema 键：证据响应只暴露 DTO 键，不泄漏领域结构。
 */
class InvestigationResponseMapperTest {

    private final InvestigationResponseMapper mapper = new InvestigationResponseMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evidenceResponseExposesDtoSchemaKeysOnly() throws Exception {
        LogTemplateSummary summary = new LogTemplateSummary(1, "device <*> insert failed", 1, "t1", "t2",
                Map.of("ERROR", 1), List.of("device 1 insert failed"), new ParameterDistribution(Map.of()), false);
        LogEvidence log = new LogEvidence("ev-1", summary, "svc", "prod", "1.0.0", "c", "1h", null,
                "drain3-0.9", new EvidenceProvenance("drain3", "c", null, null, "v1", "2024-01-01T00:00:00Z"));
        CodeAnchor anchor = new CodeAnchor("STACK_FRAME", "AssetRepository.insert", "ev-1", 0.9, "v1");
        CodeEvidence code = new CodeEvidence("code-1", "a", "AssetRepository.insert", "F.java", 1, "c", "ex",
                "VERIFIED");
        EvidenceBundle bundle = new EvidenceBundle("svc", "prod", "1.0.0", "c", "1h", List.of(log),
                List.of(anchor), List.of(code), List.of(), List.of(), false);

        EvidenceResponse response = mapper.toEvidence(bundle);
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"available\":true").contains("\"service\":\"svc\"")
                .contains("\"evidenceId\":\"ev-1\"").contains("\"clusterId\":1")
                .contains("\"template\":\"device <*> insert failed\"")
                .contains("\"provenance\":").contains("\"source\":\"drain3\"")
                .contains("\"anchors\":").contains("\"status\":\"VERIFIED\"")
                .contains("\"degradations\":").contains("\"contradictions\":").contains("\"truncated\":false");
        assertThat(json).doesNotContain("valuesByMask", "hasVerifiedSource", "\"summary\":{");
    }
}
