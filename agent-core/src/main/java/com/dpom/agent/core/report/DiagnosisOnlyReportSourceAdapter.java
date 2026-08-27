package com.dpom.agent.core.report;

import com.dpom.agent.core.authority.AuthorityId;
import com.dpom.agent.core.authority.InvestigationAuthority;
import com.dpom.agent.core.authority.InvestigationAuthorityStore;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.persistence.authority.AuthorityTerminalDao;
import com.dpom.agent.core.persistence.authority.DiagnosisSourceRow;
import org.springframework.stereotype.Component;

/** 只从 DPOMAgent 权威仓储装载报告源，不接受模型自由文本或外部报告正文。 */
@Component
public class DiagnosisOnlyReportSourceAdapter {
    private final InvestigationAuthorityStore store;
    private final AuthorityTerminalDao terminalDao;

    public DiagnosisOnlyReportSourceAdapter(InvestigationAuthorityStore store, AuthorityTerminalDao terminalDao) {
        this.store = store;
        this.terminalDao = terminalDao;
    }

    public DiagnosisOnlyReportSource load(String investigationId) {
        AuthorityId id = new AuthorityId(investigationId);
        InvestigationAuthority.Snapshot snapshot = store.find(id)
                .orElseThrow(() -> new IllegalArgumentException("REPORT_INVESTIGATION_NOT_FOUND"))
                .snapshot();
        DiagnosisSourceRow source = terminalDao.findSource(investigationId)
                .orElseThrow(() -> new IllegalStateException("REPORT_DIAGNOSIS_SOURCE_MISSING"));
        boolean terminal = snapshot.status() == InvestigationStatus.COMPLETED
                || snapshot.status() == InvestigationStatus.INCONCLUSIVE;
        if (!terminal || snapshot.conclusion() == null) {
            throw new IllegalStateException("REPORT_TERMINAL_INVESTIGATION_REQUIRED");
        }
        if (source.aggregateVersion() != snapshot.version()
                || !source.investigationId().equals(snapshot.investigationId().value())) {
            throw new IllegalStateException("REPORT_SOURCE_AUTHORITY_MISMATCH");
        }
        return new DiagnosisOnlyReportSource(snapshot, source);
    }
}
