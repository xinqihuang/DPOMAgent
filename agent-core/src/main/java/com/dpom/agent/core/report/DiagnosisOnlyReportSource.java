package com.dpom.agent.core.report;

import com.dpom.agent.core.authority.InvestigationAuthority;
import com.dpom.agent.core.persistence.authority.DiagnosisSourceRow;

/** 诊断报告唯一允许读取的已持久化权威事实。 */
public record DiagnosisOnlyReportSource(InvestigationAuthority.Snapshot investigation,
                                        DiagnosisSourceRow diagnosisSource) {
}
