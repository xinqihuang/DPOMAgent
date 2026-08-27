package com.dpom.agent.core.report;

import java.util.List;

/** 创建 diagnosis-only 不可变报告修订的请求。 */
public record DiagnosisOnlyReportCommand(String investigationId, String requestIdempotencyKey,
                                         long expectedRevision, List<String> changeReasons) {
    public DiagnosisOnlyReportCommand {
        if (investigationId == null || investigationId.isBlank()) throw new IllegalArgumentException("REPORT_INVESTIGATION_REQUIRED");
        if (requestIdempotencyKey == null || requestIdempotencyKey.isBlank() || requestIdempotencyKey.length() > 128)
            throw new IllegalArgumentException("REPORT_IDEMPOTENCY_KEY_INVALID");
        if (expectedRevision < 0) throw new IllegalArgumentException("REPORT_EXPECTED_REVISION_INVALID");
        changeReasons = changeReasons == null ? List.of() : List.copyOf(changeReasons);
        if (changeReasons.size() > 8 || changeReasons.stream().anyMatch(value -> value == null
                || !value.matches("[A-Z][A-Z0-9_]{0,63}"))) {
            throw new IllegalArgumentException("REPORT_CHANGE_REASON_INVALID");
        }
    }
}
