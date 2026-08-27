package com.dpom.agent.web.authorityapi;

import com.dpom.agent.core.authority.AuthorityId;
import com.dpom.agent.core.authority.DiagnosisSourceProjection;
import com.dpom.agent.core.persistence.authority.AuthorityAuditViewRow;
import com.dpom.agent.core.persistence.authority.AuthorityHeadRow;
import com.dpom.agent.core.persistence.authority.AuthorityTerminalDao;
import com.dpom.agent.core.persistence.authority.DiagnosisSourceRow;
import com.dpom.agent.core.persistence.authority.InvestigationAuthorityDao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Authority 对外只读查询与响应脱敏服务。 */
@Service
public class AuthorityReadService {

    private final InvestigationAuthorityDao authorityDao;
    private final AuthorityTerminalDao terminalDao;
    private final ObjectMapper objectMapper;

    /** 创建只读服务。 */
    public AuthorityReadService(InvestigationAuthorityDao authorityDao, AuthorityTerminalDao terminalDao,
            ObjectMapper objectMapper) {
        this.authorityDao = authorityDao;
        this.terminalDao = terminalDao;
        this.objectMapper = objectMapper;
    }

    /** 读取有界进度页，额外读取一行以计算 hasMore。 */
    @Transactional(readOnly = true)
    public AuthorityProgressPage progress(AuthorityId investigationId, long after, int limit) {
        if (after < 0 || limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid progress cursor or limit");
        }
        AuthorityHeadRow head = authorityDao.findHead(investigationId.value())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<AuthorityAuditViewRow> rows = authorityDao.findAuditPage(investigationId.value(), after, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<AuthorityProgressItem> items = rows.stream().limit(limit).map(AuthorityReadService::item).toList();
        long next = items.isEmpty() ? after : items.get(items.size() - 1).sequence();
        return new AuthorityProgressPage(investigationId.value(), head.aggregateVersion(), head.status(),
                after, next, hasMore, items);
    }

    /** 读取、校验并保守脱敏不可变诊断源。 */
    @Transactional(readOnly = true)
    public DiagnosisSourceView diagnosisSource(AuthorityId investigationId) {
        DiagnosisSourceRow row = terminalDao.findSource(investigationId.value())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        verifyDocument(row);
        DiagnosisSourceProjection source = parse(row);
        if (!source.investigationId().equals(investigationId)
                || !source.sourceDigest().equals(row.sourceSha256())) {
            throw new IllegalStateException("DIAGNOSIS_SOURCE_IDENTITY_MISMATCH");
        }
        return redact(source);
    }

    private static AuthorityProgressItem item(AuthorityAuditViewRow row) {
        return new AuthorityProgressItem(row.sequenceNumber(), row.aggregateVersion(), row.auditKind(),
                row.entityId(), row.reasonCode(), row.occurredAt());
    }

    private DiagnosisSourceProjection parse(DiagnosisSourceRow row) {
        try {
            return objectMapper.readValue(row.sourceJson(), DiagnosisSourceProjection.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("DIAGNOSIS_SOURCE_JSON_INVALID", e);
        }
    }

    private static void verifyDocument(DiagnosisSourceRow row) {
        byte[] actual = sha256(row.sourceJson().getBytes(StandardCharsets.UTF_8));
        byte[] expected;
        try {
            expected = HexFormat.of().parseHex(row.documentSha256());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("DIAGNOSIS_SOURCE_DOCUMENT_DIGEST_INVALID", e);
        }
        if (!MessageDigest.isEqual(actual, expected)) {
            throw new IllegalStateException("DIAGNOSIS_SOURCE_DOCUMENT_DIGEST_MISMATCH");
        }
    }

    private static DiagnosisSourceView redact(DiagnosisSourceProjection source) {
        boolean changed = false;
        AuthoritySafeText.RedactedText rootCause = AuthoritySafeText.redact(source.rootCause());
        changed |= rootCause.changed();
        List<DiagnosisSourceProjection.SupportingObservation> observations = new ArrayList<>();
        for (DiagnosisSourceProjection.SupportingObservation item : source.supportingObservations()) {
            AuthoritySafeText.RedactedText summary = AuthoritySafeText.redact(item.summary());
            changed |= summary.changed();
            observations.add(new DiagnosisSourceProjection.SupportingObservation(item.observationId(),
                    item.source(), item.evidenceReference(), item.evidenceSha256(), summary.value()));
        }
        RedactedList alternatives = redact(source.alternatives());
        RedactedList gaps = redact(source.evidenceGaps());
        changed |= alternatives.changed() || gaps.changed();
        DiagnosisSourceProjection safe = new DiagnosisSourceProjection(source.sourceId(), source.contractVersion(),
                source.investigationId(), source.incidentId(), source.aggregateVersion(), source.status(),
                source.runId(), source.conclusionId(), source.disposition(), rootCause.value(), observations,
                alternatives.values(), gaps.values(), source.provenance(), source.committedAt(),
                source.sourceDigest());
        return new DiagnosisSourceView(safe, changed);
    }

    private static RedactedList redact(List<String> values) {
        boolean changed = false;
        List<String> result = new ArrayList<>();
        for (String value : values) {
            AuthoritySafeText.RedactedText item = AuthoritySafeText.redact(value);
            result.add(item.value());
            changed |= item.changed();
        }
        return new RedactedList(List.copyOf(result), changed);
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA256_UNAVAILABLE", e);
        }
    }

    private record RedactedList(List<String> values, boolean changed) {
    }
}

