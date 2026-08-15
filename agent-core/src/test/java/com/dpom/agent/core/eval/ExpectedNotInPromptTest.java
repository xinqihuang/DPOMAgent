package com.dpom.agent.core.eval;

import com.dpom.agent.common.llm.ChatMessage;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.llm.ModelTurnRequest;
import com.dpom.agent.common.llm.ModelTurnResult;
import com.dpom.agent.core.investigation.InvestigationContext;
import com.dpom.agent.core.investigation.SymptomBrain;
import com.dpom.agent.core.logevidence.CodeEvidence;
import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.dpom.agent.core.logevidence.EvidenceProvenance;
import com.dpom.agent.core.logevidence.LogEvidence;
import com.dpom.agent.core.logevidence.LogTemplateSummary;
import com.dpom.agent.core.logevidence.ParameterDistribution;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 负向验收：expected.json 的 rootCauseId 不得进入 LLM prompt。
 */
class ExpectedNotInPromptTest {

    @Test
    void expectedRootCauseIdNotInjectedIntoPrompt() {
        String sentinel = "SecretRootCauseMarker";
        LogTemplateSummary s = new LogTemplateSummary(1, "device <*> insert failed", 1, null, null,
                Map.of("ERROR", 1), List.of("device 1 insert failed"), new ParameterDistribution(Map.of()), false);
        LogEvidence log = new LogEvidence("ev-1", s, "svc", "prod", "1.0.0", "c", "1h", null, "drain3-0.9",
                new EvidenceProvenance("drain3", "c", null, null, "v1", null));
        CodeEvidence code = new CodeEvidence("code-1", "a", "AssetRepository.insert", "F.java", 1, "c", "ex", "VERIFIED");
        EvidenceBundle bundle = new EvidenceBundle("svc", "prod", "1.0.0", "c", "1h", List.of(log), List.of(),
                List.of(code), List.of(), List.of(), false);

        AtomicReference<ModelTurnRequest> captured = new AtomicReference<>();
        ModelClient llm = request -> {
            captured.set(request);
            return new ModelTurnResult(ChatMessage.assistant("{\"type\":\"wait\",\"reason\":\"x\"}"));
        };
        SymptomBrain brain = new SymptomBrain(llm, "device create transaction rollback");
        InvestigationContext context = new InvestigationContext(null, List.of(), List.of(), List.of(), bundle);
        brain.decide(context);

        String content = captured.get().messages().stream().map(ChatMessage::content).collect(Collectors.joining("\n"));
        assertThat(content).doesNotContain(sentinel);
        assertThat(content).contains("AssetRepository.insert");
    }
}
