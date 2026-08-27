package com.dpom.agent.core.report;

import java.util.List;
import java.util.Set;

/** 诊断报告 v1 的封闭版本、Judge 与缺口目录。 */
public final class DiagnosticReportContract {
    public static final String SCHEMA_VERSION = "1.0.0";
    public static final String TEMPLATE_VERSION = "diagnostic-report-standard@1.0.0";
    public static final List<String> REQUIRED_JUDGES = List.of(
            "ROOT_CAUSE_CORRECTNESS", "EVIDENCE_SUFFICIENCY", "TIMELINE_CONSISTENCY",
            "RECOMMENDATION_SAFETY", "RULE_INVARIANTS", "SCHEMA_INTEGRITY");
    public static final Set<String> GAP_CODES = Set.of(
            "MISSING_REQUIRED_EVIDENCE", "MISSING_REQUIRED_JUDGE", "UNAVAILABLE_JUDGE",
            "STALE_SOURCE", "INTEGRITY_MISMATCH", "UNSUPPORTED_SOURCE_VERSION",
            "MISSING_PROVENANCE", "REDACTED_REQUIRED_CONTENT", "INCOMPATIBLE_EVALUATION");

    private DiagnosticReportContract() {
    }
}
