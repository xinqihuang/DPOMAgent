package com.dpom.agent.core.investigation;

import com.dpom.agent.core.hypothesis.Hypothesis;
import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.dpom.agent.core.observation.Observation;

import java.util.List;

/**
 * 调查上下文：供决策大脑观察的当前快照。
 *
 * @param investigation  调查
 * @param steps          已记录步骤
 * @param observations   已有观察
 * @param hypotheses     已有假设
 * @param evidenceBundle 日志到代码证据束（可为空）
 */
public record InvestigationContext(Investigation investigation, List<InvestigationStep> steps,
                                   List<Observation> observations, List<Hypothesis> hypotheses,
                                   EvidenceBundle evidenceBundle) {
}
