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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

/** 原子提交终态权威状态、诊断源和未绑定传输方式的发布意图。 */
@Service
public class DiagnosisTerminalCommitService {

    private static final String EVENT_TYPE = "investigation.terminal";

    private final InvestigationAuthorityStore authorityStore;
    private final AuthorityTerminalDao terminalDao;
    private final DiagnosisSourceBuilder sourceBuilder;
    private final ObjectMapper objectMapper;
    private final Rfc8785CanonicalJsonWriter canonicalJsonWriter;

    /** 创建终态提交服务。 */
    public DiagnosisTerminalCommitService(InvestigationAuthorityStore authorityStore,
            AuthorityTerminalDao terminalDao, DiagnosisSourceBuilder sourceBuilder, ObjectMapper objectMapper) {
        this.authorityStore = authorityStore;
        this.terminalDao = terminalDao;
        this.sourceBuilder = sourceBuilder;
        this.objectMapper = objectMapper;
        this.canonicalJsonWriter = new Rfc8785CanonicalJsonWriter(objectMapper);
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
        PublicationIntentRow intent = new PublicationIntentRow(intentId.value(),
                source.investigationId().value(), source.aggregateVersion(), EVENT_TYPE,
                source.sourceId().value(), source.sourceDigest(), "PENDING", committedAt, committedAt);
        requireInserted(terminalDao.insertIntent(intent), "DIAGNOSIS_INTENT_INSERT_CONFLICT");
        return source;
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
}
