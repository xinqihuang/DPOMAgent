package com.dpom.agent.core.diagnosissource;

import com.dpom.agent.core.authority.AuthorityId;
import com.dpom.agent.core.authority.DiagnosisSourceProjection;
import com.dpom.agent.core.authority.InvestigationAuthority;
import com.dpom.agent.core.authority.InvestigationAuthorityStore;
import com.dpom.agent.core.diagnosisevent.Rfc8785CanonicalJsonWriter;
import com.dpom.agent.core.persistence.authority.AuthorityTerminalDao;
import com.dpom.agent.core.persistence.authority.DiagnosisSourceRow;
import com.dpom.agent.core.persistence.authority.PublicationIntentRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

/** 原子提交终态权威状态、诊断源和未绑定传输方式的发布意图。 */
@Service
public class DiagnosisTerminalCommitService {

    private static final String EVENT_TYPE = "investigation.terminal";

    private final InvestigationAuthorityStore authorityStore;
    private final AuthorityTerminalDao terminalDao;
    private final DiagnosisSourceBuilder sourceBuilder;
    private final ObjectMapper objectMapper;
    private final Rfc8785CanonicalJsonWriter canonicalJsonWriter;
    private final String producerIdentity;
    private final String authorityEpoch;

    /** 创建终态提交服务。 */
    public DiagnosisTerminalCommitService(InvestigationAuthorityStore authorityStore,
            AuthorityTerminalDao terminalDao, DiagnosisSourceBuilder sourceBuilder, ObjectMapper objectMapper,
            @Value("${dpom.authority.publication.producer-identity:dpom-agent-local}") String producerIdentity,
            @Value("${dpom.authority.publication.epoch:phase1b-local}") String authorityEpoch) {
        this.authorityStore = authorityStore;
        this.terminalDao = terminalDao;
        this.sourceBuilder = sourceBuilder;
        this.objectMapper = objectMapper;
        this.canonicalJsonWriter = new Rfc8785CanonicalJsonWriter(objectMapper);
        this.producerIdentity = identifier(producerIdentity, "PUBLICATION_PRODUCER_IDENTITY_INVALID");
        this.authorityEpoch = identifier(authorityEpoch, "PUBLICATION_AUTHORITY_EPOCH_INVALID");
    }

    /**
     * 在同一数据库事务中保存终态、不可变诊断源和唯一 PENDING 发布意图。
     *
     * @return 已提交诊断源
     */
    @Transactional
    public DiagnosisSourceProjection commit(InvestigationAuthority authority, long expectedVersion) {
        DiagnosisSourceProjection source = sourceBuilder.build(authority.snapshot());
        byte[] canonical = canonicalJsonWriter.write(objectMapper.valueToTree(source));
        authorityStore.save(authority, expectedVersion);
        LocalDateTime committedAt = LocalDateTime.ofInstant(source.committedAt(), ZoneOffset.UTC);
        DiagnosisSourceRow sourceRow = new DiagnosisSourceRow(source.sourceId().value(),
                source.investigationId().value(), source.aggregateVersion(), source.contractVersion(),
                new String(canonical, StandardCharsets.UTF_8), source.sourceDigest(), sha256(canonical), committedAt);
        requireInserted(terminalDao.insertSource(sourceRow), "DIAGNOSIS_SOURCE_INSERT_CONFLICT");
        AuthorityId intentId = AuthorityId.derive("publication-intent", source.investigationId().value(),
                Long.toString(source.aggregateVersion()), EVENT_TYPE);
        FrozenEvent event = frozenEvent(source, sourceRow, intentId);
        PublicationIntentRow intent = new PublicationIntentRow(intentId.value(),
                source.investigationId().value(), 1L, "investigation.completed",
                source.sourceId().value(), source.sourceDigest(), "dpom.diagnosis-event.v2",
                event.idempotencyKey(), "2.0", event.content(), event.sha256(),
                "PENDING", 0, committedAt, null, null, null, null, null, committedAt, committedAt);
        requireInserted(terminalDao.insertIntent(intent), "DIAGNOSIS_INTENT_INSERT_CONFLICT");
        return source;
    }

    private FrozenEvent frozenEvent(DiagnosisSourceProjection source, DiagnosisSourceRow sourceRow,
            AuthorityId intentId) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("eventId", UUID.nameUUIDFromBytes(intentId.value().getBytes(StandardCharsets.UTF_8)).toString());
        root.put("eventType", "investigation.completed");
        root.put("schemaVersion", "2.0");
        root.put("occurredAt", source.committedAt().toString());
        root.putObject("producer").put("service", "DPOMAgent").put("instanceId", producerIdentity);
        root.putObject("sourceAuthority").put("service", "DPOMAgent")
                .put("authorityEpoch", authorityEpoch).put("aggregateVersion", source.aggregateVersion())
                .put("publicationIntentId", intentId.value());
        root.put("incidentId", source.incidentId().value());
        root.put("investigationId", source.investigationId().value());
        root.put("runId", source.runId().value());
        root.put("aggregateSequence", 1L);
        String idempotencyKey = source.investigationId().value() + ".completed." + source.aggregateVersion();
        root.put("idempotencyKey", idempotencyKey);
        root.set("provenance", provenance(source));
        root.putObject("evidenceManifest").put("manifestId", source.sourceId().value())
                .put("schemaVersion", "1.0").put("sha256", sourceRow.documentSha256())
                .put("byteSize", sourceRow.sourceJson().getBytes(StandardCharsets.UTF_8).length)
                .put("sensitivity", "RESTRICTED").put("retentionClass", "EVAL_90D");
        ObjectNode content = root.putObject("inlinePayload").put("payloadType", "diagnosis-summary")
                .put("payloadSchemaVersion", "1.0").putObject("content");
        content.put("conclusionRef", source.conclusionId().value());
        content.put("resultType", source.disposition().name());
        content.put("summary", source.rootCause());
        ArrayNode evidenceRefs = content.putArray("evidenceRefs");
        source.supportingObservations().forEach(value -> evidenceRefs.add(value.evidenceReference()));
        byte[] canonical = canonicalJsonWriter.write(root);
        if (canonical.length > 65_536) {
            throw new IllegalStateException("PUBLICATION_EVENT_TOO_LARGE");
        }
        return new FrozenEvent(idempotencyKey, new String(canonical, StandardCharsets.UTF_8), sha256(canonical));
    }

    private ObjectNode provenance(DiagnosisSourceProjection source) {
        ObjectNode value = objectMapper.createObjectNode();
        available(value.putObject("application"), "DPOMAgent", "authority-v1");
        component(value.putObject("model"), source, "model");
        component(value.putObject("prompt"), source, "prompt");
        unavailable(value.putArray("skills").addObject());
        component(value.putArray("toolContracts").addObject(), source, "toolset");
        unavailable(value.putObject("source"));
        available(value.putObject("evidenceSchema"), "evidence-manifest", "1.0");
        return value;
    }

    private void component(ObjectNode target, DiagnosisSourceProjection source, String componentId) {
        String version = source.provenance().stream().filter(value -> componentId.equals(value.componentId()))
                .map(DiagnosisSourceProjection.ComponentProvenance::componentVersion).findFirst().orElse(null);
        if (version == null || version.isBlank()) {
            unavailable(target);
        } else {
            available(target, componentId, version);
        }
    }

    private static void available(ObjectNode target, String name, String version) {
        target.put("status", "available").put("name", name).put("version", version);
    }

    private static void unavailable(ObjectNode target) {
        target.put("status", "unavailable").put("reasonCode", "NOT_RECORDED");
    }

    private static String identifier(String value, String code) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException(code);
        }
        return value;
    }

    private static void requireInserted(int affectedRows, String code) {
        if (affectedRows != 1) {
            throw new IllegalStateException(code);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA256_UNAVAILABLE", e);
        }
    }

    private record FrozenEvent(String idempotencyKey, String content, String sha256) { }
}
