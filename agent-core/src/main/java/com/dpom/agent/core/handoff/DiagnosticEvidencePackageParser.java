package com.dpom.agent.core.handoff;

import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.dpom.agent.core.logevidence.EvidenceProvenance;
import com.dpom.agent.core.logevidence.LogEvidence;
import com.dpom.agent.core.logevidence.LogTemplateSummary;
import com.dpom.agent.core.logevidence.ParameterDistribution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把校验通过的证据包恢复为现有 EvidenceBundle（源码由研发侧准确快照补充，不来自证据包）。
 */
public class DiagnosticEvidencePackageParser {

    private static final String SOURCE = "evidence-package";

    /**
     * 恢复为 EvidenceBundle。
     *
     * @param pkg 校验通过的内容
     * @return 可进入既有调查流程的证据束
     */
    public EvidenceBundle recover(RecoveredEvidencePackage pkg) {
        List<String> logs = pkg.sections().getOrDefault("logs", List.of());
        List<LogEvidence> logEvidences = new ArrayList<>(logs.size());
        int seq = 0;
        for (String template : logs) {
            logEvidences.add(toLogEvidence("ep-" + (++seq), template, pkg));
        }
        List<String> degradations = pkg.sections().getOrDefault("degradations", List.of());
        List<String> contradictions = pkg.sections().getOrDefault("contradictions", List.of());
        return new EvidenceBundle(pkg.service(), pkg.environment(), pkg.release(), pkg.commit(), pkg.timeRange(),
                logEvidences, List.of(), List.of(), degradations, contradictions, false);
    }

    private LogEvidence toLogEvidence(String id, String template, RecoveredEvidencePackage pkg) {
        LogTemplateSummary summary = new LogTemplateSummary(0, template, 1, null, null,
                Map.of("INFO", 1), List.of(template), new ParameterDistribution(Map.of()), false);
        return new LogEvidence(id, summary, pkg.service(), pkg.environment(), pkg.release(), pkg.commit(),
                pkg.timeRange(), null, "evidence-package-1",
                new EvidenceProvenance(SOURCE, pkg.commit(), null, null, "1", null));
    }
}
