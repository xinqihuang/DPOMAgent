package com.dpom.agent.core.report;

import com.dpom.agent.core.diagnosisevent.Rfc8785CanonicalJsonWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 对规范诊断报告执行引用、状态、修订、安全与摘要语义的失败关闭校验。 */
public final class DiagnosticReportValidator {
    private static final Pattern PROHIBITED = Pattern.compile(
            "(?i)(authorization\\s*[:=]|bearer\\s+|api[_-]?key|password|secret[_-]?key|raw model|prompt body)");
    private final Rfc8785CanonicalJsonWriter canonical;

    /** 使用标准 ObjectMapper 创建校验器。 */
    public DiagnosticReportValidator() {
        this(new ObjectMapper());
    }

    /** 使用应用 ObjectMapper 创建校验器。 */
    public DiagnosticReportValidator(ObjectMapper objectMapper) {
        canonical = new Rfc8785CanonicalJsonWriter(objectMapper);
    }

    /** 校验单个报告的跨字段语义和内容摘要。 */
    public void validate(JsonNode report) {
        require(report.isObject(), "REPORT_OBJECT_REQUIRED");
        require(DiagnosticReportContract.SCHEMA_VERSION.equals(text(report, "schemaVersion")),
                "REPORT_VERSION_UNSUPPORTED");
        rejectProhibited(report);
        Set<String> evidence = identities(report.path("evidenceReferences"), "evidenceId");
        validateClaims(report.path("observations"), evidence);
        validateClaims(report.path("hypotheses"), evidence);
        validateConclusions(report.path("conclusions"), evidence);
        validateRecommendations(report.path("recommendations"), evidence);
        validateCompleteness(report);
        validateEvaluation(report);
        validateRevision(report);
        require(digest(report).equals(text(report, "reportDigest")), "REPORT_DIGEST_MISMATCH");
    }

    /** 校验同一报告流的替代链存在、无环且版本严格递增。 */
    public void validateRevisionChain(List<JsonNode> reports) {
        Set<String> ids = new HashSet<>();
        reports.forEach(report -> require(ids.add(text(report, "reportId")), "REPORT_REVISION_DUPLICATE"));
        for (JsonNode report : reports) {
            JsonNode parent = report.path("supersedesReportId");
            if (!parent.isMissingNode() && !parent.isNull()) {
                JsonNode predecessor = reports.stream()
                        .filter(candidate -> parent.asText().equals(text(candidate, "reportId")))
                        .findFirst().orElseThrow(() -> failure("REPORT_SUPERSESSION_MISSING"));
                require(predecessor.path("revision").asLong() < report.path("revision").asLong(),
                        "REPORT_REVISION_NOT_INCREASING");
            }
        }
        reports.forEach(report -> walk(report, reports, new HashSet<>()));
    }

    /** 计算排除顶层 reportDigest 后的 RFC 8785 SHA-256。 */
    public String digest(JsonNode report) {
        try {
            ObjectNode copy = report.deepCopy();
            copy.remove("reportDigest");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.write(copy)));
        } catch (Exception exception) {
            throw new IllegalStateException("REPORT_DIGEST_UNAVAILABLE", exception);
        }
    }

    private void validateClaims(JsonNode claims, Set<String> evidence) {
        require(claims.isArray(), "REPORT_CLAIMS_REQUIRED");
        identities(claims, "itemId");
        claims.forEach(claim -> validateRefs(claim, evidence));
    }

    private void validateConclusions(JsonNode conclusions, Set<String> evidence) {
        validateClaims(conclusions, evidence);
        conclusions.forEach(claim -> {
            if ("CONFIRMED".equals(text(claim, "disposition"))) {
                require(!claim.path("supportingEvidenceRefs").isEmpty(), "REPORT_CONFIRMED_UNSUPPORTED");
            }
        });
    }

    private void validateRecommendations(JsonNode recommendations, Set<String> evidence) {
        identities(recommendations, "itemId");
        recommendations.forEach(item -> item.path("supportingEvidenceRefs").forEach(
                ref -> require(evidence.contains(ref.asText()), "REPORT_EVIDENCE_ORPHAN")));
    }

    private void validateRefs(JsonNode claim, Set<String> evidence) {
        claim.path("supportingEvidenceRefs").forEach(
                ref -> require(evidence.contains(ref.asText()), "REPORT_EVIDENCE_ORPHAN"));
        claim.path("contradictingEvidenceRefs").forEach(
                ref -> require(evidence.contains(ref.asText()), "REPORT_EVIDENCE_ORPHAN"));
    }

    private void validateCompleteness(JsonNode report) {
        Set<String> gaps = new HashSet<>();
        report.path("gapCodes").forEach(gap -> {
            require(DiagnosticReportContract.GAP_CODES.contains(gap.asText()), "REPORT_GAP_UNSUPPORTED");
            require(gaps.add(gap.asText()), "REPORT_GAP_DUPLICATE");
        });
        require("COMPLETE".equals(text(report, "completeness")) == gaps.isEmpty(),
                "REPORT_COMPLETENESS_GAP_MISMATCH");
    }

    private void validateEvaluation(JsonNode report) {
        JsonNode evaluation = report.path("evaluation");
        String profile = text(report, "reportProfile");
        if ("DIAGNOSIS_ONLY".equals(profile)) {
            require("NOT_REQUIRED".equals(text(evaluation, "outcome")), "REPORT_EVALUATION_NOT_REQUIRED");
            require(evaluation.path("judges").isEmpty(), "REPORT_DIAGNOSIS_ONLY_JUDGES");
            return;
        }
        require("DIAGNOSIS_EVALUATED".equals(profile), "REPORT_PROFILE_UNSUPPORTED");
        JsonNode lineage = evaluation.path("lineage");
        require(lineage.hasNonNull("evalCaseId") && lineage.path("evalCaseVersion").asLong() > 0
                && lineage.hasNonNull("evalRunId") && lineage.hasNonNull("datasetId")
                && lineage.path("datasetVersion").asLong() > 0 && lineage.hasNonNull("datasetMembershipDigest")
                && lineage.hasNonNull("replayPlanId") && lineage.hasNonNull("replayRunId")
                && lineage.hasNonNull("suiteId") && lineage.hasNonNull("suiteVersion")
                && lineage.hasNonNull("diagnosisSourceReportId")
                && lineage.path("diagnosisSourceRevision").asLong() > 0
                && lineage.hasNonNull("diagnosisSourceDigest"), "REPORT_EVALUATION_LINEAGE_MISSING");
        identities(evaluation.path("judges"), "judgeResultId");
        Set<String> kinds = new HashSet<>();
        evaluation.path("judges").forEach(judge -> kinds.add(text(judge, "kind")));
        boolean available = kinds.containsAll(DiagnosticReportContract.REQUIRED_JUDGES)
                && noneUnavailable(evaluation.path("judges"));
        if ("COMPLETE".equals(text(report, "completeness"))) {
            require(available, "REPORT_REQUIRED_JUDGE_MISSING");
            require(!"INCOMPLETE".equals(text(evaluation, "outcome")), "REPORT_EVALUATION_INCOMPLETE");
        }
        if ("PASS".equals(text(evaluation, "outcome"))) {
            require(available && allPass(evaluation.path("judges")), "REPORT_PASS_INFERRED");
        }
    }

    private void validateRevision(JsonNode report) {
        int revision = report.path("revision").asInt();
        JsonNode parent = report.path("supersedesReportId");
        require(revision == 1 || (!parent.isMissingNode() && !parent.isNull()), "REPORT_SUPERSESSION_REQUIRED");
        require(revision == 1 || !report.path("changeReasons").isEmpty(), "REPORT_CHANGE_REASON_REQUIRED");
    }

    private void rejectProhibited(JsonNode node) {
        if (node.isTextual()) {
            require(!PROHIBITED.matcher(node.asText()).find(), "REPORT_PROHIBITED_CONTENT");
        } else if (node.isContainerNode()) {
            node.forEach(this::rejectProhibited);
        }
    }

    private Set<String> identities(JsonNode array, String field) {
        require(array.isArray(), "REPORT_IDENTITY_COLLECTION_REQUIRED");
        Set<String> values = new HashSet<>();
        array.forEach(item -> require(values.add(text(item, field)), "REPORT_IDENTITY_DUPLICATE"));
        return values;
    }

    private boolean noneUnavailable(JsonNode judges) {
        for (JsonNode judge : judges) {
            if (Set.of("UNAVAILABLE", "INCOMPLETE").contains(text(judge, "status"))) {
                return false;
            }
        }
        return true;
    }

    private boolean allPass(JsonNode judges) {
        for (JsonNode judge : judges) {
            if (!"PASS".equals(text(judge, "status"))) {
                return false;
            }
        }
        return true;
    }

    private void walk(JsonNode report, List<JsonNode> reports, Set<String> path) {
        String id = text(report, "reportId");
        require(path.add(id), "REPORT_REVISION_CYCLE");
        JsonNode parent = report.path("supersedesReportId");
        if (!parent.isMissingNode() && !parent.isNull()) {
            reports.stream().filter(candidate -> parent.asText().equals(text(candidate, "reportId")))
                    .findFirst().ifPresent(candidate -> walk(candidate, reports, path));
        }
        path.remove(id);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        require(value.isTextual() && !value.asText().isBlank(),
                "REPORT_" + field.toUpperCase(Locale.ROOT) + "_REQUIRED");
        return value.asText();
    }

    private IllegalArgumentException failure(String reason) {
        return new IllegalArgumentException(reason);
    }

    private void require(boolean condition, String reason) {
        if (!condition) {
            throw failure(reason);
        }
    }
}
