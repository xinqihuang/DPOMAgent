package com.dpom.agent.core.report;

import com.dpom.agent.core.authority.AuthorityId;
import com.dpom.agent.core.diagnosisevent.Rfc8785CanonicalJsonWriter;
import com.dpom.agent.core.persistence.authority.AuthorityDiagnosticReportDao;
import com.dpom.agent.core.persistence.authority.DiagnosticReportHeadRow;
import com.dpom.agent.core.persistence.authority.DiagnosticReportRevisionRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

/** diagnosis-only 报告的幂等创建、乐观修订和精确历史读取服务。 */
@Service
public class DiagnosisOnlyReportService {
    private final AuthorityDiagnosticReportDao dao;
    private final DiagnosisOnlyReportSourceAdapter sourceAdapter;
    private final DiagnosisOnlyReportBuilder builder;
    private final ObjectMapper mapper;
    private final Clock clock;

    public DiagnosisOnlyReportService(AuthorityDiagnosticReportDao dao, DiagnosisOnlyReportSourceAdapter sourceAdapter,
            DiagnosisOnlyReportBuilder builder, ObjectMapper mapper) {
        this.dao = dao;
        this.sourceAdapter = sourceAdapter;
        this.builder = builder;
        this.mapper = mapper;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public DiagnosticReportRevisionRow create(DiagnosisOnlyReportCommand command) {
        DiagnosisOnlyReportSource source = sourceAdapter.load(command.investigationId());
        String fingerprint = fingerprint(command, source.diagnosisSource().sourceSha256());
        var priorRequest = dao.findByRequestKey(command.investigationId(), command.requestIdempotencyKey());
        if (priorRequest.isPresent()) {
            if (!priorRequest.get().requestFingerprint().equals(fingerprint)) {
                throw new IllegalStateException("REPORT_IDEMPOTENCY_CONFLICT");
            }
            return priorRequest.get();
        }
        DiagnosticReportHeadRow head = dao.findHead(command.investigationId()).orElse(null);
        long currentRevision = head == null ? 0 : head.latestRevision();
        if (command.expectedRevision() != currentRevision) throw new IllegalStateException("REPORT_VERSION_CONFLICT");
        long revision = currentRevision + 1;
        if (revision > 1 && command.changeReasons().isEmpty()) throw new IllegalArgumentException("REPORT_CHANGE_REASON_REQUIRED");
        String reportId = AuthorityId.derive("diagnostic-report", command.investigationId(), Long.toString(revision)).value();
        String predecessor = head == null ? null : head.latestReportId();
        LocalDateTime createdAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        var built = builder.build(source, reportId, revision, predecessor, command.changeReasons(), clock.instant());
        String reasonsJson = canonicalReasons(command.changeReasons());
        DiagnosticReportRevisionRow row = new DiagnosticReportRevisionRow(reportId, command.investigationId(),
                source.diagnosisSource().sourceId(), command.requestIdempotencyKey(), fingerprint, revision,
                predecessor, reasonsJson, built.canonicalContent(), built.reportDigest(),
                source.diagnosisSource().sourceSha256(), createdAt);
        if (dao.insertRevision(row) != 1) throw new IllegalStateException("REPORT_REVISION_INSERT_FAILED");
        if (head == null) {
            if (dao.insertHead(new DiagnosticReportHeadRow(command.investigationId(), reportId, 1, 0,
                    createdAt, createdAt)) != 1) throw new IllegalStateException("REPORT_HEAD_INSERT_FAILED");
        } else if (dao.advanceHead(command.investigationId(), currentRevision, head.lockVersion(), reportId,
                createdAt) != 1) {
            throw new IllegalStateException("REPORT_VERSION_CONFLICT");
        }
        return row;
    }

    @Transactional(readOnly = true)
    public DiagnosticReportRevisionRow find(String reportId) {
        return dao.findRevision(reportId).orElseThrow(() -> new IllegalArgumentException("REPORT_NOT_FOUND"));
    }

    @Transactional(readOnly = true)
    public List<DiagnosticReportRevisionRow> history(String investigationId, long afterRevision, int limit) {
        if (afterRevision < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("REPORT_PAGE_INVALID");
        return dao.findHistoryPage(investigationId, afterRevision, limit);
    }

    private String canonicalReasons(List<String> reasons) {
        return new String(new Rfc8785CanonicalJsonWriter(mapper).write(mapper.valueToTree(
                reasons.stream().distinct().sorted().toList())), StandardCharsets.UTF_8);
    }

    private String fingerprint(DiagnosisOnlyReportCommand command, String sourceDigest) {
        try {
            String value = command.investigationId() + "\n" + command.expectedRevision() + "\n"
                    + canonicalReasons(command.changeReasons()) + "\n" + sourceDigest;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("REPORT_FINGERPRINT_UNAVAILABLE", exception);
        }
    }
}
