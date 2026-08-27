package com.dpom.agent.core.investigation;

import com.dpom.agent.common.diagnosisevent.DiagnosisEventProvenance;
import com.dpom.agent.common.diagnosisevent.ProvenanceSource;
import com.dpom.agent.common.diagnosisevent.ProvenanceVersion;
import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.diagnosisevent.BuiltDiagnosisEvent;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventBuildMetadata;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventBuilder;
import com.dpom.agent.core.diagnosisevent.Rfc8785CanonicalJsonWriter;
import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.DiagnosisEventAuditDao;
import com.dpom.agent.core.persistence.DiagnosisEventOutboxDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.InvestigationRunDao;
import com.dpom.agent.core.persistence.command.ConclusionInsert;
import com.dpom.agent.core.persistence.command.DiagnosisEventAuditInsert;
import com.dpom.agent.core.persistence.command.DiagnosisEventOutboxInsert;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * 在一个短数据库事务中提交调查终态及其评测事件。
 */
@Service
public class InvestigationTerminalizationService {

    private final InvestigationDao investigationDao;
    private final IncidentDao incidentDao;
    private final InvestigationRunDao runDao;
    private final ConclusionDao conclusionDao;
    private final DiagnosisEventOutboxDao outboxDao;
    private final DiagnosisEventAuditDao auditDao;
    private final InvestigationStateMachine stateMachine;
    private final DiagnosisEventBuilder eventBuilder;

    /**
     * 构造事务服务。
     */
    public InvestigationTerminalizationService(InvestigationDao investigationDao, IncidentDao incidentDao,
            InvestigationRunDao runDao, ConclusionDao conclusionDao, DiagnosisEventOutboxDao outboxDao,
            DiagnosisEventAuditDao auditDao, InvestigationStateMachine stateMachine, ObjectMapper objectMapper) {
        this.investigationDao = investigationDao;
        this.incidentDao = incidentDao;
        this.runDao = runDao;
        this.conclusionDao = conclusionDao;
        this.outboxDao = outboxDao;
        this.auditDao = auditDao;
        this.stateMachine = stateMachine;
        ObjectMapper eventMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.eventBuilder = new DiagnosisEventBuilder(eventMapper, new Rfc8785CanonicalJsonWriter(eventMapper));
    }

    /**
     * 原子提交终态；重复调用返回已提交结果，不生成新事件。
     *
     * @param command 终态命令
     */
    @Transactional
    public void terminalize(InvestigationTerminalizationCommand command) {
        Investigation locked = investigationDao.findByIdForUpdate(command.investigationId()).orElseThrow();
        if (isTerminal(locked.status())) {
            verifyAlreadyCommitted(locked);
            return;
        }
        validateTarget(command.terminalStatus());
        transition(locked, command.terminalStatus());
        Conclusion conclusion = insertConclusion(command);
        InvestigationRun run = finishAndLoadRun(command, locked);
        if (eligible(command.terminalStatus())) {
            persistEvent(locked, conclusion, run);
        }
    }

    private void transition(Investigation investigation, InvestigationStatus terminal) {
        InvestigationStatus current = investigation.status();
        if (eligible(terminal) && stateMachine.canTransition(current, InvestigationStatus.SYNTHESIZING)) {
            investigationDao.updateStatus(investigation.id(), InvestigationStatus.SYNTHESIZING);
            current = InvestigationStatus.SYNTHESIZING;
        }
        if (eligible(terminal)) {
            stateMachine.assertTransition(current, terminal);
        }
        investigationDao.updateStatus(investigation.id(), terminal);
    }

    private Conclusion insertConclusion(InvestigationTerminalizationCommand command) {
        ConclusionInsert insert = new ConclusionInsert(command.investigationId(), command.resultType(),
                command.rootCauseId(), command.rootCause(), command.evidenceIds(), null, command.summary());
        conclusionDao.insert(insert);
        return conclusionDao.findById(insert.getId()).orElseThrow();
    }

    private InvestigationRun finishAndLoadRun(InvestigationTerminalizationCommand command, Investigation locked) {
        Long runId = command.runId() == null ? locked.currentRunId() : command.runId();
        if (runId == null) {
            if (eligible(command.terminalStatus())) {
                throw new IllegalStateException("TERMINAL_RUN_MISSING");
            }
            return null;
        }
        runDao.finish(runId, LocalDateTime.now());
        return runDao.findById(runId).orElseThrow();
    }

    private void persistEvent(Investigation locked, Conclusion conclusion, InvestigationRun run) {
        Investigation terminal = investigationDao.findById(locked.id()).orElseThrow();
        Incident incident = incidentDao.findById(locked.incidentId()).orElseThrow();
        OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        BuiltDiagnosisEvent built = eventBuilder.build(incident, terminal, conclusion, run,
                new DiagnosisEventBuildMetadata(UUID.randomUUID().toString(), occurredAt, "dpom-agent-local", 1),
                provenance(incident, run));
        DiagnosisEventOutboxInsert outbox = new DiagnosisEventOutboxInsert(built.event().eventId(),
                built.event().idempotencyKey(), terminal.id(), run.id(), built.event().eventType(),
                built.event().aggregateSequence(), built.event().schemaVersion(),
                new String(built.canonicalBytes(), StandardCharsets.UTF_8), built.canonicalSha256(),
                occurredAt.toLocalDateTime());
        outboxDao.insert(outbox);
        auditDao.append(new DiagnosisEventAuditInsert(built.event().eventId(), built.event().eventType(),
                "CREATED", "SUCCESS", null, null, null, null));
    }

    private DiagnosisEventProvenance provenance(Incident incident, InvestigationRun run) {
        ProvenanceVersion unavailable = ProvenanceVersion.unavailable("NOT_RECORDED");
        ProvenanceVersion model = version("investigation-model", run.modelVersion());
        ProvenanceVersion prompt = version("investigation-prompt", run.promptVersion());
        ProvenanceVersion tools = version("investigation-toolset", run.toolsetVersion());
        return new DiagnosisEventProvenance(ProvenanceVersion.available("DPOMAgent", "0.1.0", null),
                model, prompt, List.of(unavailable), List.of(tools),
                ProvenanceSource.available(incident.serviceCode(), incident.releaseVersion(), incident.commitSha()),
                ProvenanceVersion.available("diagnostic-evidence-package", "1.0", null));
    }

    private ProvenanceVersion version(String name, String value) {
        return value == null || value.isBlank()
                ? ProvenanceVersion.unavailable("NOT_RECORDED") : ProvenanceVersion.available(name, value, null);
    }

    private void verifyAlreadyCommitted(Investigation investigation) {
        if (eligible(investigation.status()) && outboxDao.findByInvestigationId(investigation.id()).size() != 1) {
            throw new IllegalStateException("TERMINAL_EVENT_MISSING");
        }
    }

    private void validateTarget(InvestigationStatus status) {
        if (!isTerminal(status)) {
            throw new IllegalArgumentException("目标状态不是终态");
        }
    }

    private boolean eligible(InvestigationStatus status) {
        return status == InvestigationStatus.COMPLETED || status == InvestigationStatus.INCONCLUSIVE;
    }

    private boolean isTerminal(InvestigationStatus status) {
        return eligible(status) || status == InvestigationStatus.FAILED || status == InvestigationStatus.CANCELLED;
    }
}
