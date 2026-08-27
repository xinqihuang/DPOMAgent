package com.dpom.agent.core.persistence.authority;

import com.dpom.agent.core.authority.AuthorityId;
import com.dpom.agent.core.authority.InvestigationAuthority;
import com.dpom.agent.core.authority.InvestigationAuthorityStore;
import com.dpom.agent.core.diagnosisevent.Rfc8785CanonicalJsonWriter;
import com.dpom.agent.core.diagnosisprogress.AuthorityProgressIntentFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** MyBatis 实现的 Investigation 权威聚合仓储。 */
@Repository
public class MyBatisInvestigationAuthorityStore implements InvestigationAuthorityStore {

    private final InvestigationAuthorityDao dao;
    private final AuthorityProgressDao progressDao;
    private final AuthorityProgressIntentFactory progressIntentFactory;
    private final ObjectMapper objectMapper;
    private final Rfc8785CanonicalJsonWriter canonicalJsonWriter;

    /** 创建仓储。 */
    public MyBatisInvestigationAuthorityStore(InvestigationAuthorityDao dao, AuthorityProgressDao progressDao,
            AuthorityProgressIntentFactory progressIntentFactory, ObjectMapper objectMapper) {
        this.dao = dao;
        this.progressDao = progressDao;
        this.progressIntentFactory = progressIntentFactory;
        this.objectMapper = objectMapper;
        this.canonicalJsonWriter = new Rfc8785CanonicalJsonWriter(objectMapper);
    }

    @Override
    @Transactional
    public void create(InvestigationAuthority authority) {
        InvestigationAuthority.Snapshot snapshot = authority.snapshot();
        EncodedSnapshot encoded = encode(snapshot);
        if (dao.insertHead(head(snapshot, encoded)) != 1) {
            throw new IllegalStateException("AUTHORITY_CREATE_CONFLICT");
        }
        insertRevision(snapshot, encoded);
        appendHistories(snapshot, 0L, Set.of(), true);
    }

    @Override
    @Transactional
    public void save(InvestigationAuthority authority, long expectedVersion) {
        InvestigationAuthority.Snapshot snapshot = authority.snapshot();
        if (snapshot.version() <= expectedVersion) {
            throw new IllegalArgumentException("AUTHORITY_VERSION_NOT_ADVANCED");
        }
        String investigationId = snapshot.investigationId().value();
        Long lastAuditSequence = dao.findMaxAuditSequence(investigationId);
        Set<String> toolUseIds = new HashSet<>(dao.findToolUseIds(investigationId));
        boolean progressAdmitted = progressDao.hasAdmission(investigationId);
        EncodedSnapshot encoded = encode(snapshot);
        if (dao.updateHead(head(snapshot, encoded), expectedVersion) != 1) {
            throw new IllegalStateException("AUTHORITY_VERSION_CONFLICT");
        }
        insertRevision(snapshot, encoded);
        appendHistories(snapshot, lastAuditSequence == null ? 0L : lastAuditSequence, toolUseIds,
                progressAdmitted);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InvestigationAuthority> find(AuthorityId investigationId) {
        return dao.findHead(investigationId.value())
                .map(row -> InvestigationAuthority.restore(decode(row.snapshotJson(), row.snapshotSha256())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvestigationAuthority.Snapshot> history(AuthorityId investigationId) {
        return dao.findRevisions(investigationId.value()).stream()
                .map(row -> decode(row.snapshotJson(), row.snapshotSha256()))
                .toList();
    }

    private AuthorityHeadRow head(InvestigationAuthority.Snapshot snapshot, EncodedSnapshot encoded) {
        String currentRunId = snapshot.currentRunId() == null ? null : snapshot.currentRunId().value();
        return new AuthorityHeadRow(snapshot.investigationId().value(), snapshot.incident().id().value(),
                snapshot.version(), snapshot.status().name(), currentRunId, snapshot.stepsUsed(),
                snapshot.toolCallsUsed(), snapshot.noProgressRounds(), encoded.json(), encoded.sha256(),
                utc(snapshot.createdAt()), utc(snapshot.updatedAt()));
    }

    private void insertRevision(InvestigationAuthority.Snapshot snapshot, EncodedSnapshot encoded) {
        AuthorityRevisionRow row = new AuthorityRevisionRow(snapshot.investigationId().value(), snapshot.version(),
                snapshot.status().name(), encoded.json(), encoded.sha256(), utc(snapshot.updatedAt()));
        if (dao.insertRevision(row) != 1) {
            throw new IllegalStateException("AUTHORITY_REVISION_CONFLICT");
        }
    }

    private void appendHistories(InvestigationAuthority.Snapshot snapshot, long lastAuditSequence,
            Set<String> existingToolUseIds, boolean progressAdmitted) {
        for (InvestigationAuthority.AuditRecord record : snapshot.audit()) {
            if (record.sequence() > lastAuditSequence) {
                AuthorityAuditRow row = new AuthorityAuditRow(record.id().value(),
                        record.investigationId().value(), record.sequence(), record.aggregateVersion(),
                        record.kind().name(), record.entityId().value(), record.reasonCode(),
                        utc(record.occurredAt()));
                requireInserted(dao.insertAudit(row), "AUTHORITY_AUDIT_CONFLICT");
                if (progressAdmitted) {
                    requireInserted(progressDao.insertIntent(progressIntentFactory.create(snapshot, record)),
                            "AUTHORITY_PROGRESS_INTENT_CONFLICT");
                }
            }
        }
        for (InvestigationAuthority.ToolUseState toolUse : snapshot.toolUses()) {
            if (!existingToolUseIds.contains(toolUse.id().value())) {
                AuthorityToolUseRow row = new AuthorityToolUseRow(toolUse.id().value(),
                        toolUse.investigationId().value(), toolUse.runId().value(), toolUse.toolName(),
                        toolUse.contractVersion(), toolUse.argumentSha256(), encodeValue(toolUse.argumentNames()),
                        toolUse.argumentSizeBytes(), toolUse.targetScope(), toolUse.correlationId(),
                        toolUse.status().name(), toolUse.reasonCode(),
                        encodeValue(toolUse.evidenceReferences()), utc(toolUse.occurredAt()));
                requireInserted(dao.insertToolUse(row), "AUTHORITY_TOOL_USE_CONFLICT");
            }
        }
    }

    private EncodedSnapshot encode(InvestigationAuthority.Snapshot snapshot) {
        byte[] canonical = canonicalJsonWriter.write(objectMapper.valueToTree(snapshot));
        return new EncodedSnapshot(new String(canonical, StandardCharsets.UTF_8), sha256(canonical));
    }

    private String encodeValue(Object value) {
        byte[] canonical = canonicalJsonWriter.write(objectMapper.valueToTree(value));
        return new String(canonical, StandardCharsets.UTF_8);
    }

    private InvestigationAuthority.Snapshot decode(String json, String expectedSha256) {
        byte[] encoded = json.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(sha256(encoded).getBytes(StandardCharsets.US_ASCII),
                expectedSha256.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalStateException("AUTHORITY_SNAPSHOT_DIGEST_MISMATCH");
        }
        try {
            return objectMapper.readValue(encoded, InvestigationAuthority.Snapshot.class);
        } catch (IOException e) {
            throw new IllegalStateException("AUTHORITY_SNAPSHOT_JSON_INVALID", e);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA256_UNAVAILABLE", e);
        }
    }

    private static LocalDateTime utc(java.time.Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static void requireInserted(int affectedRows, String code) {
        if (affectedRows != 1) {
            throw new IllegalStateException(code);
        }
    }

    private record EncodedSnapshot(String json, String sha256) {
    }
}
