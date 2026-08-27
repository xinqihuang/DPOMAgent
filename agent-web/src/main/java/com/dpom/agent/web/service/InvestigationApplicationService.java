package com.dpom.agent.web.service;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.dpom.agent.common.runtime.RuntimeEvidenceClient;
import com.dpom.agent.core.cache.SnapshotCache;
import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationCoordinator;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.investigation.InvestigationStep;
import com.dpom.agent.core.investigation.InvestigationTerminalizationCommand;
import com.dpom.agent.core.investigation.InvestigationTerminalizationService;
import com.dpom.agent.core.investigation.SymptomBrain;
import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.dpom.agent.core.logevidence.EvidenceBundleBuilder;
import com.dpom.agent.core.logevidence.LogEvidenceService;
import com.dpom.agent.core.persistence.ApiRequestRecord;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.EvidenceBundleDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationApiRequestDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.EvidenceBundleCodec;
import com.dpom.agent.core.persistence.InvestigationStepDao;
import com.dpom.agent.core.persistence.command.ApiRequestInsert;
import com.dpom.agent.core.persistence.command.ConclusionInsert;
import com.dpom.agent.core.persistence.command.EvidenceBundleInsert;
import com.dpom.agent.core.persistence.command.IncidentInsert;
import com.dpom.agent.core.persistence.command.InvestigationInsert;
import com.dpom.agent.core.tool.InvestigationToolExecutor;
import com.dpom.agent.core.workspace.CodeWorkspace;
import com.dpom.agent.web.dto.InvestigationResponse;
import com.dpom.agent.web.dto.InvestigationSubmitRequest;
import com.dpom.agent.web.filter.CorrelationIdFilter;
import com.dpom.agent.web.metrics.ErrorCodes;
import com.dpom.agent.web.metrics.InvestigationMetrics;
import com.dpom.agent.web.validation.InputValidator;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * 调查 API 应用编排层：提交、异步执行、查询；负责业务指标埋点与 correlationId 异步传播。
 *
 * <p>提交事务化：Incident/Investigation/api_request 同事务落库，idempotency_key 唯一约束为并发仲裁；
 * 执行器派发在事务提交后，拒绝时事务化补偿为 FAILED/REJECTED。终态计数以 DB「非终态→终态」条件更新为唯一仲裁。</p>
 */
@Service
public class InvestigationApplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(InvestigationApplicationService.class);
    private static final String MINER_VERSION = "drain3-mcp-0.9";
    private static final String AUTO_KEY_PREFIX = "!auto-";

    private final IncidentDao incidentDao;
    private final InvestigationDao investigationDao;
    private final EvidenceBundleDao evidenceBundleDao;
    private final ConclusionDao conclusionDao;
    private final InvestigationStepDao stepDao;
    private final InvestigationApiRequestDao apiRequestDao;
    private final InvestigationCoordinator coordinator;
    private final InvestigationTerminalizationService terminalizationService;
    private final ModelClient modelClient;
    private final CodeGraphClient codeGraphClient;
    private final CodeWorkspace workspace;
    private final LogTemplateMinerClient logTemplateMinerClient;
    private final RuntimeEvidenceClient runtimeEvidenceClient;
    private final SnapshotCache snapshotCache;
    private final ThreadPoolTaskExecutor investigationExecutor;
    private final TransactionTemplate transactionTemplate;
    private final InvestigationMetrics metrics;
    private final InputValidator validator = new InputValidator();

    public InvestigationApplicationService(IncidentDao incidentDao, InvestigationDao investigationDao,
            EvidenceBundleDao evidenceBundleDao, ConclusionDao conclusionDao, InvestigationStepDao stepDao,
            InvestigationApiRequestDao apiRequestDao, InvestigationCoordinator coordinator,
            InvestigationTerminalizationService terminalizationService, ModelClient modelClient,
            CodeGraphClient codeGraphClient, CodeWorkspace workspace, LogTemplateMinerClient logTemplateMinerClient,
            RuntimeEvidenceClient runtimeEvidenceClient, SnapshotCache snapshotCache,
            @Qualifier("investigationExecutor") ThreadPoolTaskExecutor investigationExecutor,
            PlatformTransactionManager transactionManager, InvestigationMetrics metrics) {
        this.incidentDao = incidentDao;
        this.investigationDao = investigationDao;
        this.evidenceBundleDao = evidenceBundleDao;
        this.conclusionDao = conclusionDao;
        this.stepDao = stepDao;
        this.apiRequestDao = apiRequestDao;
        this.coordinator = coordinator;
        this.terminalizationService = terminalizationService;
        this.modelClient = modelClient;
        this.codeGraphClient = codeGraphClient;
        this.workspace = workspace;
        this.logTemplateMinerClient = logTemplateMinerClient;
        this.runtimeEvidenceClient = runtimeEvidenceClient;
        this.snapshotCache = snapshotCache;
        this.investigationExecutor = investigationExecutor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.metrics = metrics;
    }

    public long submit(InvestigationSubmitRequest req) {
        List<String> errors = validator.validate(req);
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", errors));
        }
        String hash = payloadHash(req);
        String userKey = req.idempotencyKey() == null || req.idempotencyKey().isBlank() ? null : req.idempotencyKey();
        Submission submission = claimOrCreate(req, hash, userKey);
        if (submission.fresh()) {
            try {
                metrics.recordSubmitted();
            } catch (RuntimeException ignored) {
                // 提交指标失败不得阻止派发，避免 CREATED/api_request SUBMITTED 孤儿
            }
            dispatchOrReject(submission, req);
        }
        return submission.investigationId();
    }

    public InvestigationResponse summary(long investigationId) {
        Investigation inv = investigationDao.findById(investigationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "investigation not found"));
        Incident incident = incidentDao.findById(inv.incidentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "incident not found"));
        return new InvestigationResponse(inv.id(), inv.status().name(), incident.serviceCode(), incident.symptom(),
                inv.createdAt(), inv.updatedAt());
    }

    public List<InvestigationStep> steps(long investigationId) {
        if (investigationDao.findById(investigationId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "investigation not found");
        }
        return stepDao.findByInvestigationId(investigationId);
    }

    public Optional<EvidenceBundle> evidence(long investigationId) {
        if (investigationDao.findById(investigationId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "investigation not found");
        }
        return evidenceBundleDao.findBundleJson(investigationId).map(EvidenceBundleCodec::decode);
    }

    public Optional<Conclusion> conclusion(long investigationId) {
        if (investigationDao.findById(investigationId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "investigation not found");
        }
        return conclusionDao.findByInvestigationId(investigationId);
    }

    /** 事务化创建或幂等命中。唯一约束为并发最终仲裁。 */
    private Submission claimOrCreate(InvestigationSubmitRequest req, String hash, String userKey) {
        for (int attempt = 0; ; attempt++) {
            String key = userKey != null ? userKey : AUTO_KEY_PREFIX + UUID.randomUUID();
            Optional<ApiRequestRecord> existing = userKey == null ? Optional.empty()
                    : apiRequestDao.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                return replay(existing.get(), hash);
            }
            try {
                SubmitResult created = transactionTemplate.execute(status -> createSubmitted(req, hash, key));
                return new Submission(created.investigationId(), created.apiRequestId(), true);
            } catch (DataIntegrityViolationException e) {
                if (userKey != null) {
                    Optional<ApiRequestRecord> winner = apiRequestDao.findByIdempotencyKey(key);
                    if (winner.isPresent()) {
                        return replay(winner.get(), hash);
                    }
                    throw e;
                }
                if (attempt >= 2) {
                    throw e;
                }
            }
        }
    }

    private Submission replay(ApiRequestRecord existing, String hash) {
        if (existing.payloadHash().equals(hash)) {
            return new Submission(existing.investigationId(), existing.id(), false);
        }
        throw new InvestigationConflictException(existing.investigationId());
    }

    private SubmitResult createSubmitted(InvestigationSubmitRequest req, String hash, String key) {
        IncidentInsert incidentCommand = new IncidentInsert(req.serviceCode(), req.environment(),
                req.release(), req.commit(), req.symptom());
        incidentDao.insert(incidentCommand);
        long incidentId = incidentCommand.getId();
        InvestigationInsert investigationCommand = new InvestigationInsert(incidentId,
                InvestigationStatus.CREATED, null, 30, 60, 1800, 5);
        investigationDao.insert(investigationCommand);
        long investigationId = investigationCommand.getId();
        ApiRequestInsert apiRequestCommand = new ApiRequestInsert(key, hash, investigationId, "SUBMITTED");
        apiRequestDao.insert(apiRequestCommand);
        long apiRequestId = apiRequestCommand.getId();
        return new SubmitResult(investigationId, apiRequestId);
    }

    private void dispatchOrReject(Submission submission, InvestigationSubmitRequest req) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        try {
            investigationExecutor.execute(() -> execute(submission.investigationId(), submission.apiRequestId(),
                    req.logs(), req.timeRange(), correlationId));
        } catch (RejectedExecutionException e) {
            reject(submission.investigationId(), submission.apiRequestId());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "capacity full");
        }
    }

    /** 队列满补偿：事务化标记 FAILED + REJECTED 结论 + api_request REJECTED，不留 CREATED/RUNNING 孤儿。 */
    private void reject(long investigationId, long apiRequestId) {
        int affected = transactionTemplate.execute(status -> {
            int n = investigationDao.updateStatusIfActive(investigationId, InvestigationStatus.FAILED);
            if (n == 1) {
                ConclusionInsert conclusionCommand = new ConclusionInsert(investigationId, "REJECTED", null,
                        null, null, null, "执行队列已满，任务被拒绝");
                conclusionDao.insert(conclusionCommand);
            }
            apiRequestDao.updateDone(apiRequestId, "REJECTED", "CAPACITY_FULL");
            return n;
        });
        if (affected == 1) {
            try {
                metrics.recordTerminated("REJECTED", "REJECTED");
            } catch (RuntimeException ignored) {
                // 指标失败不得覆盖 CAPACITY_FULL/503 稳定错误契约
            }
        }
    }

    private void execute(long investigationId, long apiRequestId, List<String> logs, String timeRange,
            String correlationId) {
        if (correlationId != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        }
        Timer.Sample sample = null;
        try {
            sample = metrics.startExecution();
        } catch (RuntimeException ignored) {
            // best-effort，允许 sample 缺失，业务继续执行并收口
        }
        InvestigationStatus outcome = InvestigationStatus.FAILED;
        String errorCode = ErrorCodes.NONE;
        boolean determined = false;
        try {
            apiRequestDao.updateRunning(apiRequestId);
            Investigation inv = investigationDao.findById(investigationId).orElseThrow();
            Incident incident = incidentDao.findById(inv.incidentId()).orElseThrow();
            CodeSnapshot snapshot = snapshotCache.get(incident.serviceCode(), incident.commitSha())
                    .orElseGet(() -> {
                        CodeSnapshot resolved = codeGraphClient.resolveSnapshot(incident.serviceCode(),
                                incident.commitSha());
                        snapshotCache.put(resolved);
                        return resolved;
                    });
            LogEvidenceService pipeline = new LogEvidenceService(logTemplateMinerClient, codeGraphClient, workspace,
                    new EvidenceBundleBuilder(1_000_000));
            EvidenceBundle bundle = pipeline.run(incident.serviceCode(), incident.environment(),
                    incident.releaseVersion(), incident.commitSha(), timeRange, MINER_VERSION, snapshot, logs);
            EvidenceBundleInsert bundleCommand = new EvidenceBundleInsert(investigationId, bundle.service(),
                    bundle.commit(), EvidenceBundleCodec.encode(bundle));
            evidenceBundleDao.insert(bundleCommand);
            SymptomBrain brain = new SymptomBrain(modelClient, incident.symptom());
            InvestigationToolExecutor toolExecutor = new InvestigationToolExecutor(snapshot.snapshotId(),
                    Path.of(snapshot.workspacePath()), incident.serviceCode(), incident.environment(),
                    codeGraphClient, workspace, runtimeEvidenceClient, logTemplateMinerClient);
            coordinator.run(investigationId, brain, toolExecutor);
            outcome = investigationDao.findById(investigationId).orElseThrow().status();
            determined = true;
        } catch (Exception e) {
            errorCode = ErrorCodes.execution(e);
            LOG.error("investigation execution failed investigationId={} errorCode={}", investigationId, errorCode);
            if (!determined) {
                terminalizeFailure(investigationId);
                outcome = investigationDao.findById(investigationId).orElseThrow().status();
            }
        } finally {
            try {
                String dbErrorCode = ErrorCodes.NONE.equals(errorCode) ? null : errorCode;
                try {
                    apiRequestDao.updateDone(apiRequestId, toApiStatus(outcome), dbErrorCode);
                } catch (RuntimeException ignored) {
                    // api_request 收口为 best-effort
                }
                recordExecution(sample, investigationId, outcome, errorCode);
            } finally {
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            }
        }
    }

    private void terminalizeFailure(long investigationId) {
        Investigation investigation = investigationDao.findById(investigationId).orElseThrow();
        if (investigation.status() == InvestigationStatus.WAITING_FOR_HUMAN
                || isTerminal(investigation.status())) {
            return;
        }
        if (conclusionDao.findByInvestigationId(investigationId).isEmpty()) {
            terminalizationService.terminalize(new InvestigationTerminalizationCommand(investigationId,
                    investigation.currentRunId(), InvestigationStatus.FAILED, "FAILED",
                    null, null, "执行失败", null));
        }
    }

    /** 每次执行尝试恰好停止一次 timer；terminal counter 仅当该次执行观察到/产生终态时记一次。 */
    private void recordExecution(Timer.Sample sample, long investigationId, InvestigationStatus outcome,
            String errorCode) {
        String statusTag = statusTag(outcome);
        String resultType = resultTypeTag(investigationId, outcome);
        metrics.stopExecution(sample, statusTag, resultType, errorCode);
        if (isTerminal(outcome)) {
            metrics.recordTerminated(statusTag, resultType);
        }
    }

    private boolean isTerminal(InvestigationStatus status) {
        return status == InvestigationStatus.COMPLETED || status == InvestigationStatus.INCONCLUSIVE
                || status == InvestigationStatus.FAILED || status == InvestigationStatus.CANCELLED;
    }

    private String statusTag(InvestigationStatus status) {
        return switch (status) {
            case COMPLETED -> "COMPLETED";
            case INCONCLUSIVE -> "INCONCLUSIVE";
            case FAILED, CANCELLED -> "FAILED";
            case WAITING_FOR_HUMAN -> "WAITING_FOR_HUMAN";
            default -> "FAILED";
        };
    }

    private String resultTypeTag(long investigationId, InvestigationStatus status) {
        if (status == InvestigationStatus.FAILED || status == InvestigationStatus.CANCELLED) {
            return "FAILED";
        }
        if (status == InvestigationStatus.WAITING_FOR_HUMAN) {
            return "NONE";
        }
        try {
            return conclusionDao.findByInvestigationId(investigationId)
                    .map(Conclusion::resultType).filter(Objects::nonNull)
                    .orElse(status == InvestigationStatus.COMPLETED ? "ROOT_CAUSE_FOUND" : "INCONCLUSIVE");
        } catch (RuntimeException e) {
            return status == InvestigationStatus.COMPLETED ? "ROOT_CAUSE_FOUND" : "INCONCLUSIVE";
        }
    }

    private String toApiStatus(InvestigationStatus status) {
        return switch (status) {
            case COMPLETED -> "COMPLETED";
            case INCONCLUSIVE -> "INCONCLUSIVE";
            case FAILED, CANCELLED -> "FAILED";
            default -> "RUNNING";
        };
    }

    private String payloadHash(InvestigationSubmitRequest req) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            sb.append(req.serviceCode()).append('|').append(req.environment()).append('|').append(req.release())
                    .append('|').append(req.commit()).append('|').append(req.symptom()).append('|')
                    .append(req.timeRange()).append('|').append(req.logs() == null ? "" : String.join("\n", req.logs()));
            return HexFormat.of().formatHex(md.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("hash failed", e);
        }
    }

    /** 一次提交的落地结果：调查 id + api_request id。 */
    private record SubmitResult(long investigationId, long apiRequestId) { }

    /** 幂等命中或新提交的结果；fresh 标记是否需要派发执行。 */
    private record Submission(long investigationId, long apiRequestId, boolean fresh) { }
}
