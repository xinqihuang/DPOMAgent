package com.dpom.agent.core.report;

import com.dpom.agent.core.authority.InvestigationAuthority;
import com.dpom.agent.core.diagnosisevent.Rfc8785CanonicalJsonWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 将终态 Investigation 事实确定性投影为 diagnosis-only 报告。 */
@Component
public class DiagnosisOnlyReportBuilder {
    private final ObjectMapper mapper;
    private final DiagnosticReportValidator validator;
    private final Rfc8785CanonicalJsonWriter canonical;

    public DiagnosisOnlyReportBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
        this.validator = new DiagnosticReportValidator(mapper);
        this.canonical = new Rfc8785CanonicalJsonWriter(mapper);
    }

    public BuiltReport build(DiagnosisOnlyReportSource source, String reportId, long revision,
            String supersedesReportId, List<String> changeReasons, Instant generatedAt) {
        InvestigationAuthority.Snapshot snapshot = source.investigation();
        if (snapshot.observations().isEmpty()) {
            throw new IllegalStateException("REPORT_OBSERVATION_REQUIRED");
        }
        ObjectNode report = mapper.createObjectNode();
        report.put("contractType", "diagnostic-report");
        report.put("schemaVersion", DiagnosticReportContract.SCHEMA_VERSION);
        report.put("reportProfile", "DIAGNOSIS_ONLY");
        report.put("reportId", reportId);
        report.put("revision", revision);
        if (supersedesReportId == null) report.putNull("supersedesReportId");
        else report.put("supersedesReportId", supersedesReportId);
        array(report, "changeReasons", changeReasons.stream().distinct().sorted().toList());

        ObjectNode identity = report.putObject("identity");
        identity.put("incidentId", snapshot.incident().id().value());
        identity.put("investigationId", snapshot.investigationId().value());
        identity.put("runId", snapshot.currentRunId().value());
        ObjectNode target = report.putObject("target");
        target.put("provider", "HUAWEI_CLOUD");
        target.put("region", targetRegion(snapshot));
        target.put("resourceType", "SERVICE");
        target.put("resourceId", safeId(snapshot.incident().serviceCode()));
        target.put("displayName", snapshot.incident().serviceCode());
        window(report.putObject("incidentWindow"), snapshot.createdAt(), snapshot.updatedAt());

        ArrayNode timeline = report.putArray("timeline");
        snapshot.audit().stream().sorted(Comparator.comparingLong(InvestigationAuthority.AuditRecord::sequence))
                .forEach(item -> {
                    ObjectNode node = timeline.addObject();
                    node.put("itemId", item.id().value());
                    node.put("at", item.occurredAt().toString());
                    node.put("stateCode", item.kind().name());
                    node.put("summary", item.reasonCode());
                });

        ArrayNode observations = report.putArray("observations");
        snapshot.observations().stream().sorted(Comparator.comparing(item -> item.id().value())).forEach(item -> {
            ObjectNode claim = claim(observations, item.id().value(), "OBSERVATION", item.summary());
            claim.withArray("supportingEvidenceRefs").add(item.id().value());
        });
        ArrayNode hypotheses = report.putArray("hypotheses");
        snapshot.hypotheses().stream().sorted(Comparator.comparing(item -> item.id().value())).forEach(item ->
                claim(hypotheses, item.id().value(), "HYPOTHESIS", item.description()));
        ArrayNode conclusions = report.putArray("conclusions");
        InvestigationAuthority.ConclusionState conclusion = snapshot.conclusion();
        ObjectNode conclusionNode = claim(conclusions, conclusion.id().value(), "CONCLUSION",
                conclusion.rootCause());
        conclusion.supportingObservationIds().stream().map(id -> id.value()).sorted()
                .forEach(conclusionNode.withArray("supportingEvidenceRefs")::add);
        conclusionNode.put("disposition", conclusion.disposition().name());

        ArrayNode evidence = report.putArray("evidenceReferences");
        snapshot.observations().stream().sorted(Comparator.comparing(item -> item.id().value())).forEach(item -> {
            ObjectNode node = evidence.addObject();
            node.put("evidenceId", item.id().value());
            node.put("sourceCapability", safeCode(item.source()));
            node.put("sourceAdapter", safeId(item.source()));
            node.put("artifactRef", item.evidenceReference());
            node.put("sha256", item.evidenceSha256());
            node.put("collectedAt", item.createdAt().toString());
            window(node.putObject("window"), snapshot.createdAt(), snapshot.updatedAt());
            node.put("targetResourceId", safeId(snapshot.incident().serviceCode()));
            node.put("sensitivity", "CONTROLLED");
            node.put("redaction", "NONE");
        });
        List<String> gaps = conclusion.evidenceGaps().isEmpty()
                ? List.of() : List.of("MISSING_REQUIRED_EVIDENCE");
        array(report, "gapCodes", gaps);
        report.putArray("recommendations");
        ObjectNode evaluation = report.putObject("evaluation");
        evaluation.put("outcome", "NOT_REQUIRED");
        evaluation.putObject("lineage");
        evaluation.putArray("judges");
        ArrayNode provenance = report.putArray("provenance");
        snapshot.runs().stream().filter(run -> run.id().equals(snapshot.currentRunId())).findFirst()
                .ifPresent(run -> {
                    provenance(provenance, "DPOMAgent", "authority-v1", source.diagnosisSource().sourceSha256());
                    provenance(provenance, "model", safeId(run.modelVersion()), source.diagnosisSource().sourceSha256());
                    provenance(provenance, "prompt", safeId(run.promptVersion()), source.diagnosisSource().sourceSha256());
                    provenance(provenance, "toolset", safeId(run.toolsetVersion()), source.diagnosisSource().sourceSha256());
                });
        report.put("generatedAt", generatedAt.toString());
        report.put("completeness", gaps.isEmpty() ? "COMPLETE" : "INCOMPLETE");
        report.putObject("extensions");
        report.put("reportDigest", validator.digest(report));
        validator.validate(report);
        String content = new String(canonical.write(report), StandardCharsets.UTF_8);
        return new BuiltReport(content, report.path("reportDigest").asText());
    }

    private ObjectNode claim(ArrayNode parent, String id, String type, String summary) {
        ObjectNode node = parent.addObject();
        node.put("itemId", id);
        node.put("claimType", type);
        node.put("summary", summary);
        node.put("confidenceBasisPoints", 0);
        node.putArray("supportingEvidenceRefs");
        node.putArray("contradictingEvidenceRefs");
        return node;
    }

    private void provenance(ArrayNode parent, String id, String version, String digest) {
        ObjectNode node = parent.addObject();
        node.put("componentId", id);
        node.put("componentVersion", version);
        node.put("contractVersion", "diagnosis-source/v1");
        node.put("sourceDigest", digest);
    }

    private void window(ObjectNode node, Instant from, Instant to) {
        node.put("from", from.toString());
        node.put("to", to.toString());
    }

    private void array(ObjectNode parent, String name, List<String> values) {
        ArrayNode array = parent.putArray(name);
        values.forEach(array::add);
    }

    private String targetRegion(InvestigationAuthority.Snapshot snapshot) {
        return snapshot.toolUses().stream().map(InvestigationAuthority.ToolUseState::targetScope)
                .map(scope -> scope.split(":"))
                .filter(parts -> parts.length > 1 && parts[1].matches("[a-z]{2}-[a-z]+-[0-9]+"))
                .map(parts -> parts[1]).findFirst().orElse("unknown");
    }

    private String safeId(String value) {
        String normalized = value.replaceAll("[^A-Za-z0-9._:/-]", "-");
        return normalized.matches("^[A-Za-z0-9].*") ? normalized : "id-" + normalized;
    }

    private String safeCode(String value) {
        String normalized = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_./:-]", "_");
        return normalized.matches("^[A-Z].*") ? normalized : "SOURCE_" + normalized;
    }

    public record BuiltReport(String canonicalContent, String reportDigest) {
    }
}
