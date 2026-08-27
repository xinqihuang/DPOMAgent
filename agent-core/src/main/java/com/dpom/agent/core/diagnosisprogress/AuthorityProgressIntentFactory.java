package com.dpom.agent.core.diagnosisprogress;

import com.dpom.agent.core.authority.InvestigationAuthority;
import com.dpom.agent.core.diagnosisevent.Rfc8785CanonicalJsonWriter;
import com.dpom.agent.core.persistence.authority.ProgressPublicationIntentRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/** 仅从已经追加的权威审计事实构造并冻结 Diagnosis Progress v1.1。 */
@Component
public final class AuthorityProgressIntentFactory {

    static final String TOPIC = "dpom.diagnosis-progress.v1";
    static final String SCHEMA_VERSION = "1.1";
    private static final int MAX_CANONICAL_BYTES = 8 * 1024;
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private final ObjectMapper objectMapper;
    private final Rfc8785CanonicalJsonWriter canonicalJsonWriter;
    private final String authorityEpoch;

    /** 创建固定 authority epoch 的进度意图工厂。 */
    public AuthorityProgressIntentFactory(ObjectMapper objectMapper,
            @Value("${dpom.authority.publication.epoch:phase1b-local}") String authorityEpoch) {
        this.objectMapper = objectMapper;
        this.canonicalJsonWriter = new Rfc8785CanonicalJsonWriter(objectMapper);
        this.authorityEpoch = identifier(authorityEpoch, "PROGRESS_AUTHORITY_EPOCH_INVALID");
    }

    /** 将一条刚落库的审计记录冻结为同事务 Outbox 意图。 */
    public ProgressPublicationIntentRow create(InvestigationAuthority.Snapshot snapshot,
            InvestigationAuthority.AuditRecord audit) {
        String progressId = UUID.nameUUIDFromBytes(
                ("dpom-progress:" + audit.id().value()).getBytes(StandardCharsets.UTF_8)).toString();
        String runId = runId(snapshot, audit);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("progressId", progressId);
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("occurredAt", audit.occurredAt().toString());
        root.put("investigationId", audit.investigationId().value());
        if (runId != null) {
            root.put("runId", runId);
        }
        root.put("progressSequence", audit.sequence());
        root.put("aggregateVersion", audit.aggregateVersion());
        root.putObject("sourceAuthority").put("service", "DPOMAgent")
                .put("authorityEpoch", authorityEpoch);
        root.put("status", status(audit));
        root.put("stage", stage(audit));
        root.put("summaryCode", audit.kind().name());
        root.put("checkpointRef", audit.entityId().value());
        byte[] canonical = canonicalJsonWriter.write(root);
        if (canonical.length > MAX_CANONICAL_BYTES) {
            throw new IllegalStateException("PROGRESS_CANONICAL_CONTENT_TOO_LARGE");
        }
        String content = new String(canonical, StandardCharsets.UTF_8);
        String digest = sha256(canonical);
        LocalDateTime occurredAt = LocalDateTime.ofInstant(audit.occurredAt(), ZoneOffset.UTC);
        return new ProgressPublicationIntentRow(progressId, audit.id().value(), audit.investigationId().value(),
                runId, audit.sequence(), audit.aggregateVersion(), authorityEpoch, TOPIC, progressId,
                SCHEMA_VERSION, content, digest, "PENDING", 0, occurredAt, null, null, null,
                null, null, occurredAt, occurredAt);
    }

    private String runId(InvestigationAuthority.Snapshot snapshot, InvestigationAuthority.AuditRecord audit) {
        if (audit.kind() == InvestigationAuthority.AuditKind.RUN_STARTED
                || audit.kind() == InvestigationAuthority.AuditKind.RUN_ENDED) {
            return audit.entityId().value();
        }
        return snapshot.runs().stream()
                .filter(run -> !run.startedAt().isAfter(audit.occurredAt()))
                .max(Comparator.comparing(InvestigationAuthority.RunState::startedAt))
                .map(run -> run.id().value()).orElse(null);
    }

    private String status(InvestigationAuthority.AuditRecord audit) {
        if (audit.kind() == InvestigationAuthority.AuditKind.INVESTIGATION_CREATED) {
            return "ACCEPTED";
        }
        if (audit.kind() == InvestigationAuthority.AuditKind.CONCLUSION_COMMITTED) {
            return "CONFIRMED".equals(audit.reasonCode()) ? "COMPLETED" : "INCONCLUSIVE";
        }
        if (audit.kind() == InvestigationAuthority.AuditKind.STATUS_CHANGED) {
            return switch (audit.reasonCode()) {
                case "WAITING_FOR_HUMAN" -> "CHECKPOINTED";
                case "COMPLETED" -> "COMPLETED";
                case "INCONCLUSIVE" -> "INCONCLUSIVE";
                case "FAILED" -> "FAILED";
                case "CANCELLED" -> "CANCELLED";
                default -> "RUNNING";
            };
        }
        return "RUNNING";
    }

    private String stage(InvestigationAuthority.AuditRecord audit) {
        if (audit.kind() == InvestigationAuthority.AuditKind.INVESTIGATION_CREATED) {
            return "ADMISSION";
        }
        if (audit.kind() == InvestigationAuthority.AuditKind.STATUS_CHANGED) {
            return switch (audit.reasonCode()) {
                case "CREATED", "SCOPING" -> "ADMISSION";
                case "RESEARCHING" -> "EVIDENCE_COLLECTION";
                case "FORMING_HYPOTHESES" -> "HYPOTHESIS";
                case "VALIDATING", "WAITING_FOR_HUMAN" -> "VERIFICATION";
                default -> "TERMINALIZATION";
            };
        }
        return switch (audit.kind()) {
            case HYPOTHESIS_PROPOSED, HYPOTHESIS_REVISED -> "HYPOTHESIS";
            case CONCLUSION_COMMITTED, RUN_ENDED -> "TERMINALIZATION";
            case RUN_STARTED, STEP_APPENDED, OBSERVATION_APPENDED, TOOL_USE_RECORDED, BUDGET_UPDATED ->
                    "EVIDENCE_COLLECTION";
            default -> "VERIFICATION";
        };
    }

    private static String identifier(String value, String code) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(code);
        }
        return value;
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA256_UNAVAILABLE", exception);
        }
    }
}
