package com.dpom.agent.core.logevidence;

import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.EvidenceBundleDao;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 证据时间线服务：从持久化的证据束与结论组装可审计的时间线视图。
 */
@Service
public class EvidenceTimelineService {

    private final EvidenceBundleDao bundleDao;
    private final ConclusionDao conclusionDao;

    /**
     * 构造服务。
     *
     * @param bundleDao     证据束 DAO
     * @param conclusionDao 结论 DAO
     */
    public EvidenceTimelineService(EvidenceBundleDao bundleDao, ConclusionDao conclusionDao) {
        this.bundleDao = bundleDao;
        this.conclusionDao = conclusionDao;
    }

    /**
     * 组装时间线。
     *
     * @param investigationId 调查 id
     * @return 时间线视图（无证据束时为空条目）
     */
    public EvidenceTimeline timeline(long investigationId) {
        EvidenceBundle bundle = bundleDao.findByInvestigationId(investigationId).orElse(null);
        if (bundle == null) {
            return new EvidenceTimeline(null, List.of(), List.of(), null, null);
        }
        List<EvidenceTimelineEntry> entries = new ArrayList<>();
        for (LogEvidence e : bundle.logEvidences()) {
            entries.add(new EvidenceTimelineEntry(e.evidenceId(), "LOG", e.provenance().source(), bundle.release(),
                    e.commit(), e.provenance().ruleVersion(), e.minerVersion(), e.summary().truncated(), null));
        }
        for (CodeEvidence e : bundle.codeEvidences()) {
            String degradation = "VERIFIED".equals(e.status()) ? null : e.status();
            entries.add(new EvidenceTimelineEntry(e.evidenceId(), "SOURCE", "codegraph", bundle.release(),
                    e.commit(), null, null, false, degradation));
        }
        Conclusion conclusion = conclusionDao.findByInvestigationId(investigationId).orElse(null);
        String resultType = conclusion == null ? null : conclusion.resultType();
        String evidenceIds = conclusion == null ? null : conclusion.evidenceIds();
        return new EvidenceTimeline(bundle.release(), entries, bundle.degradations(), resultType, evidenceIds);
    }
}
