package com.dpom.agent.web.dto;

import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.investigation.InvestigationStep;
import com.dpom.agent.core.logevidence.CodeAnchor;
import com.dpom.agent.core.logevidence.CodeEvidence;
import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.dpom.agent.core.logevidence.EvidenceProvenance;
import com.dpom.agent.core.logevidence.LogEvidence;
import com.dpom.agent.core.logevidence.LogTemplateSummary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 领域对象 -> API DTO 映射：保持 API 与领域类型完全隔离。
 */
@Component
public class InvestigationResponseMapper {

    public StepResponse toStep(InvestigationStep step) {
        return new StepResponse(step.stepOrder(), step.stepType(), step.summary(), step.createdAt());
    }

    public EvidenceResponse toEvidence(EvidenceBundle bundle) {
        List<LogEvidenceResponse> logs = bundle.logEvidences() == null ? List.of()
                : bundle.logEvidences().stream().map(this::toLogEvidence).toList();
        List<CodeAnchorResponse> anchors = bundle.anchors() == null ? List.of()
                : bundle.anchors().stream().map(this::toAnchor).toList();
        List<CodeEvidenceResponse> code = bundle.codeEvidences() == null ? List.of()
                : bundle.codeEvidences().stream().map(this::toCodeEvidence).toList();
        return new EvidenceResponse(true, bundle.service(), bundle.environment(), bundle.release(), bundle.commit(),
                bundle.timeRange(), logs, anchors, code,
                bundle.degradations() == null ? List.of() : bundle.degradations(),
                bundle.contradictions() == null ? List.of() : bundle.contradictions(), bundle.truncated());
    }

    public ConclusionResponse toConclusion(Conclusion conclusion) {
        return new ConclusionResponse(true, conclusion.resultType(), conclusion.rootCauseId(), conclusion.rootCause(),
                conclusion.evidenceIds(), conclusion.summary());
    }

    public ConclusionResponse notReadyConclusion() {
        return new ConclusionResponse(false, null, null, null, null, null);
    }

    private LogEvidenceResponse toLogEvidence(LogEvidence e) {
        LogTemplateSummary s = e.summary();
        return new LogEvidenceResponse(e.evidenceId(), s.clusterId(), s.template(), s.count(), s.firstSeen(),
                s.lastSeen(), s.severityDistribution(), s.representativeSamples(),
                s.parameterDistribution() == null ? java.util.Map.of() : s.parameterDistribution().valuesByMask(),
                s.truncated(), e.service(), e.environment(), e.release(), e.commit(), e.timeRange(), e.traceIds(),
                e.minerVersion(), toProvenance(e.provenance()));
    }

    private LogEvidenceResponse.ProvenanceResponse toProvenance(EvidenceProvenance p) {
        if (p == null) {
            return null;
        }
        return new LogEvidenceResponse.ProvenanceResponse(p.source(), p.commit(), p.filePath(), p.lineNumber(),
                p.ruleVersion(), p.extractedAt());
    }

    private CodeAnchorResponse toAnchor(CodeAnchor a) {
        return new CodeAnchorResponse(a.type(), a.value(), a.sourceEvidenceId(), a.confidence(), a.ruleVersion());
    }

    private CodeEvidenceResponse toCodeEvidence(CodeEvidence e) {
        return new CodeEvidenceResponse(e.evidenceId(), e.anchorValue(), e.symbol(), e.filePath(), e.lineNumber(),
                e.commit(), e.excerpt(), e.status());
    }
}
