package com.dpom.agent.core.authority;

import com.dpom.agent.core.hypothesis.HypothesisStatus;
import com.dpom.agent.core.investigation.InvestigationStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * DPOMAgent 的纯领域 Investigation 权威聚合。
 *
 * <p>聚合只保存规范化事实、摘要、摘要哈希和引用，不保存凭据、工具原始参数、原始响应或证据正文。</p>
 */
public final class InvestigationAuthority {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");
    private static final Pattern ARGUMENT_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,63}");
    private static final Pattern SAFE_SCOPE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{2,255}");
    private static final Pattern SENSITIVE_NAME = Pattern.compile(
            "(?i).*(secret|password|passwd|credential|authorization|token|api[-_.]?key|access[-_.]?key).*"
    );
    private static final Map<InvestigationStatus, Set<InvestigationStatus>> TRANSITIONS = transitions();

    private final IncidentState incident;
    private final AuthorityId investigationId;
    private final BudgetPolicy budget;
    private final Instant createdAt;
    private final Map<AuthorityId, RunState> runs = new LinkedHashMap<>();
    private final Map<AuthorityId, StepState> steps = new LinkedHashMap<>();
    private final Map<AuthorityId, ObservationState> observations = new LinkedHashMap<>();
    private final Map<AuthorityId, HypothesisState> hypotheses = new LinkedHashMap<>();
    private final Map<AuthorityId, ToolUseState> toolUses = new LinkedHashMap<>();
    private final List<AuditRecord> audit = new ArrayList<>();

    private InvestigationStatus status;
    private AuthorityId currentRunId;
    private ConclusionState conclusion;
    private long version;
    private int stepsUsed;
    private int toolCallsUsed;
    private int noProgressRounds;
    private Instant updatedAt;

    private InvestigationAuthority(IncidentState incident, AuthorityId investigationId, BudgetPolicy budget,
            Instant createdAt) {
        this.incident = Objects.requireNonNull(incident, "incident");
        this.investigationId = Objects.requireNonNull(investigationId, "investigationId");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
        this.status = InvestigationStatus.CREATED;
        appendAudit(AuditKind.INVESTIGATION_CREATED, investigationId, "CREATED", createdAt);
    }

    /** 创建新的权威聚合。 */
    public static InvestigationAuthority create(IncidentState incident, AuthorityId investigationId,
            BudgetPolicy budget, Instant createdAt) {
        return new InvestigationAuthority(incident, investigationId, budget, createdAt);
    }

    /** 从不可变快照恢复聚合并验证全部关联不变量。 */
    public static InvestigationAuthority restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        InvestigationAuthority restored = new InvestigationAuthority(snapshot.incident(), snapshot.investigationId(),
                snapshot.budget(), snapshot.createdAt());
        restored.audit.clear();
        restored.runs.putAll(index(snapshot.runs()));
        restored.steps.putAll(indexSteps(snapshot.steps()));
        restored.observations.putAll(indexObservations(snapshot.observations()));
        restored.hypotheses.putAll(indexHypotheses(snapshot.hypotheses()));
        restored.toolUses.putAll(indexToolUses(snapshot.toolUses()));
        restored.audit.addAll(snapshot.audit());
        restored.status = snapshot.status();
        restored.currentRunId = snapshot.currentRunId();
        restored.conclusion = snapshot.conclusion();
        restored.version = snapshot.version();
        restored.stepsUsed = snapshot.stepsUsed();
        restored.toolCallsUsed = snapshot.toolCallsUsed();
        restored.noProgressRounds = snapshot.noProgressRounds();
        restored.updatedAt = snapshot.updatedAt();
        restored.validateRestoredState();
        return restored;
    }

    /** 在预期版本上推进生命周期。 */
    public void transition(long expectedVersion, InvestigationStatus target, Instant at) {
        expectVersion(expectedVersion);
        requireActive();
        Set<InvestigationStatus> allowed = TRANSITIONS.get(status);
        if (allowed == null || !allowed.contains(target)) {
            throw new IllegalStateException("AUTHORITY_TRANSITION_INVALID");
        }
        status = target;
        mutate(AuditKind.STATUS_CHANGED, investigationId, target.name(), at);
    }

    /** 开始一个带完整组件版本的新 Run。 */
    public AuthorityId startRun(long expectedVersion, String modelVersion, String promptVersion,
            String toolsetVersion, Instant at) {
        expectVersion(expectedVersion);
        requireActive();
        if (currentRunId != null && runs.get(currentRunId).endedAt() == null) {
            throw new IllegalStateException("AUTHORITY_RUN_ALREADY_ACTIVE");
        }
        AuthorityId id = AuthorityId.derive("run", investigationId.value(), Integer.toString(runs.size() + 1));
        RunState run = new RunState(id, investigationId, required(modelVersion, 128),
                required(promptVersion, 128), required(toolsetVersion, 128), at, null);
        runs.put(id, run);
        currentRunId = id;
        mutate(AuditKind.RUN_STARTED, id, "STARTED", at);
        return id;
    }

    /** 结束当前 Run。 */
    public void endRun(long expectedVersion, Instant at) {
        expectVersion(expectedVersion);
        RunState current = currentRun();
        runs.put(current.id(), current.ended(at));
        mutate(AuditKind.RUN_ENDED, current.id(), "ENDED", at);
    }

    /** 追加一个有严格顺序的调查步骤。 */
    public AuthorityId appendStep(long expectedVersion, String type, String summary, Instant at) {
        expectVersion(expectedVersion);
        requireActive();
        ensureDuration(at);
        if (stepsUsed >= budget.maxSteps()) {
            throw new IllegalStateException("AUTHORITY_STEP_BUDGET_EXHAUSTED");
        }
        RunState run = currentRun();
        int order = stepsUsed + 1;
        AuthorityId id = AuthorityId.derive("step", run.id().value(), Integer.toString(order));
        steps.put(id, new StepState(id, investigationId, run.id(), order, requiredCode(type),
                bounded(summary, 1024), at));
        stepsUsed++;
        mutate(AuditKind.STEP_APPENDED, id, type, at);
        return id;
    }

    /** 追加只含不可变引用和摘要的 Observation。 */
    public AuthorityId appendObservation(long expectedVersion, AuthorityId stepId, String source,
            String evidenceReference, String evidenceSha256, String summary, Instant at) {
        expectVersion(expectedVersion);
        requireActive();
        StepState step = require(steps, stepId, "AUTHORITY_STEP_UNKNOWN");
        String digest = sha256(evidenceSha256);
        AuthorityId id = AuthorityId.derive("observation", step.id().value(), required(source, 64),
                required(evidenceReference, 512), digest);
        if (observations.containsKey(id)) {
            throw new IllegalStateException("AUTHORITY_OBSERVATION_DUPLICATE");
        }
        observations.put(id, new ObservationState(id, investigationId, step.runId(), step.id(),
                required(source, 64), required(evidenceReference, 512), digest, bounded(summary, 1024), at));
        mutate(AuditKind.OBSERVATION_APPENDED, id, "OBSERVED", at);
        return id;
    }

    /** 提出一个不可变身份的 Hypothesis。 */
    public AuthorityId proposeHypothesis(long expectedVersion, AuthorityId parentId, String description, Instant at) {
        expectVersion(expectedVersion);
        requireActive();
        if (parentId != null) {
            require(hypotheses, parentId, "AUTHORITY_HYPOTHESIS_PARENT_UNKNOWN");
        }
        String normalized = bounded(description, 2048);
        AuthorityId id = AuthorityId.derive("hypothesis", investigationId.value(),
                parentId == null ? "ROOT" : parentId.value(), normalized);
        if (hypotheses.containsKey(id)) {
            throw new IllegalStateException("AUTHORITY_HYPOTHESIS_DUPLICATE");
        }
        hypotheses.put(id, new HypothesisState(id, investigationId, parentId, normalized,
                HypothesisStatus.PROPOSED, at, at));
        mutate(AuditKind.HYPOTHESIS_PROPOSED, id, HypothesisStatus.PROPOSED.name(), at);
        return id;
    }

    /** 追加 Hypothesis 状态修订而不改变其身份。 */
    public void reviseHypothesis(long expectedVersion, AuthorityId hypothesisId, HypothesisStatus target, Instant at) {
        expectVersion(expectedVersion);
        requireActive();
        HypothesisState current = require(hypotheses, hypothesisId, "AUTHORITY_HYPOTHESIS_UNKNOWN");
        if (!hypothesisTransitionAllowed(current.status(), target)) {
            throw new IllegalStateException("AUTHORITY_HYPOTHESIS_TRANSITION_INVALID");
        }
        hypotheses.put(hypothesisId, current.revised(target, at));
        mutate(AuditKind.HYPOTHESIS_REVISED, hypothesisId, target.name(), at);
    }

    /** 记录一次不含原始参数和响应的 ToolUse。 */
    public AuthorityId recordToolUse(long expectedVersion, ToolUseCommand command, Instant at) {
        expectVersion(expectedVersion);
        requireActive();
        ensureDuration(at);
        if (toolCallsUsed >= budget.maxToolCalls()) {
            throw new IllegalStateException("AUTHORITY_TOOL_BUDGET_EXHAUSTED");
        }
        RunState run = currentRun();
        AuthorityId id = AuthorityId.derive("tool-use", run.id().value(), Integer.toString(toolCallsUsed + 1),
                required(command.correlationId(), 128));
        ToolUseState state = new ToolUseState(id, investigationId, run.id(), required(command.toolName(), 128),
                required(command.contractVersion(), 128), sha256(command.argumentSha256()),
                argumentNames(command.argumentNames()), argumentSize(command.argumentSizeBytes()),
                targetScope(command.targetScope()), required(command.correlationId(), 128), command.status(),
                reason(command.reasonCode()), evidenceReferences(command.evidenceReferences()), at);
        toolUses.put(id, state);
        toolCallsUsed++;
        mutate(AuditKind.TOOL_USE_RECORDED, id, command.status().name(), at);
        return id;
    }

    /** 记录本轮是否产生进展，用于可恢复预算。 */
    public void recordProgress(long expectedVersion, boolean progressed, Instant at) {
        expectVersion(expectedVersion);
        requireActive();
        int nextNoProgressRounds = progressed ? 0 : noProgressRounds + 1;
        if (nextNoProgressRounds >= budget.maxNoProgressRounds()) {
            throw new IllegalStateException("AUTHORITY_NO_PROGRESS_BUDGET_EXHAUSTED");
        }
        noProgressRounds = nextNoProgressRounds;
        mutate(AuditKind.BUDGET_UPDATED, investigationId, progressed ? "PROGRESS" : "NO_PROGRESS", at);
    }

    /** 从 SYNTHESIZING 生成唯一终态 Conclusion。 */
    public AuthorityId conclude(long expectedVersion, ConclusionDisposition disposition, String rootCause,
            List<AuthorityId> supportingObservationIds, List<String> alternatives, List<String> evidenceGaps,
            Instant at) {
        expectVersion(expectedVersion);
        if (status != InvestigationStatus.SYNTHESIZING || conclusion != null) {
            throw new IllegalStateException("AUTHORITY_CONCLUSION_NOT_ALLOWED");
        }
        List<AuthorityId> evidence = List.copyOf(supportingObservationIds);
        evidence.forEach(id -> require(observations, id, "AUTHORITY_CONCLUSION_EVIDENCE_UNKNOWN"));
        if (disposition == ConclusionDisposition.CONFIRMED && evidence.isEmpty()) {
            throw new IllegalStateException("AUTHORITY_CONFIRMED_EVIDENCE_REQUIRED");
        }
        AuthorityId id = AuthorityId.derive("conclusion", investigationId.value(), disposition.name(),
                required(rootCause, 2048), evidence.toString());
        conclusion = new ConclusionState(id, investigationId, disposition, bounded(rootCause, 2048), evidence,
                boundedStrings(alternatives, 32, 512), boundedStrings(evidenceGaps, 32, 512), at);
        status = disposition == ConclusionDisposition.CONFIRMED
                ? InvestigationStatus.COMPLETED : InvestigationStatus.INCONCLUSIVE;
        mutate(AuditKind.CONCLUSION_COMMITTED, id, disposition.name(), at);
        return id;
    }

    /** 返回不可变、可持久化和可恢复的完整快照。 */
    public Snapshot snapshot() {
        return new Snapshot(incident, investigationId, version, status, currentRunId, budget, stepsUsed,
                toolCallsUsed, noProgressRounds, createdAt, updatedAt, List.copyOf(runs.values()),
                List.copyOf(steps.values()), List.copyOf(observations.values()), List.copyOf(hypotheses.values()),
                List.copyOf(toolUses.values()), conclusion, List.copyOf(audit));
    }

    /** 当前乐观版本。 */
    public long version() {
        return version;
    }

    private void expectVersion(long expectedVersion) {
        if (expectedVersion != version) {
            throw new IllegalStateException("AUTHORITY_VERSION_CONFLICT");
        }
    }

    private void requireActive() {
        if (isTerminal(status)) {
            throw new IllegalStateException("AUTHORITY_ALREADY_TERMINAL");
        }
    }

    private RunState currentRun() {
        if (currentRunId == null) {
            throw new IllegalStateException("AUTHORITY_RUN_MISSING");
        }
        return require(runs, currentRunId, "AUTHORITY_RUN_UNKNOWN");
    }

    private void ensureDuration(Instant at) {
        if (Duration.between(createdAt, at).compareTo(budget.maxDuration()) >= 0) {
            throw new IllegalStateException("AUTHORITY_DURATION_BUDGET_EXHAUSTED");
        }
    }

    private void mutate(AuditKind kind, AuthorityId entityId, String reasonCode, Instant at) {
        version++;
        updatedAt = Objects.requireNonNull(at, "at");
        appendAudit(kind, entityId, reasonCode, at);
    }

    private void appendAudit(AuditKind kind, AuthorityId entityId, String reasonCode, Instant at) {
        long sequence = audit.size() + 1L;
        AuthorityId auditId = AuthorityId.derive("audit", investigationId.value(), Long.toString(sequence),
                kind.name());
        audit.add(new AuditRecord(auditId, investigationId, sequence, version, kind, entityId,
                requiredCode(reasonCode), at));
    }

    private void validateRestoredState() {
        if (version < 0 || stepsUsed != steps.size() || toolCallsUsed != toolUses.size()) {
            throw new IllegalArgumentException("AUTHORITY_SNAPSHOT_COUNTER_INVALID");
        }
        if (audit.isEmpty() || audit.get(audit.size() - 1).aggregateVersion() != version) {
            throw new IllegalArgumentException("AUTHORITY_SNAPSHOT_AUDIT_INVALID");
        }
        for (int index = 0; index < audit.size(); index++) {
            if (audit.get(index).sequence() != index + 1L) {
                throw new IllegalArgumentException("AUTHORITY_SNAPSHOT_AUDIT_GAP");
            }
        }
        if (currentRunId != null && !runs.containsKey(currentRunId)) {
            throw new IllegalArgumentException("AUTHORITY_SNAPSHOT_RUN_INVALID");
        }
        if (conclusion != null && !isTerminal(status)) {
            throw new IllegalArgumentException("AUTHORITY_SNAPSHOT_CONCLUSION_INVALID");
        }
        runs.values().forEach(run -> sameInvestigation(run.investigationId()));
        int expectedOrder = 1;
        for (StepState step : steps.values()) {
            sameInvestigation(step.investigationId());
            require(runs, step.runId(), "AUTHORITY_SNAPSHOT_STEP_RUN_INVALID");
            if (step.order() != expectedOrder++) {
                throw new IllegalArgumentException("AUTHORITY_SNAPSHOT_STEP_ORDER_INVALID");
            }
        }
        observations.values().forEach(observation -> {
            sameInvestigation(observation.investigationId());
            require(runs, observation.runId(), "AUTHORITY_SNAPSHOT_OBSERVATION_RUN_INVALID");
            require(steps, observation.stepId(), "AUTHORITY_SNAPSHOT_OBSERVATION_STEP_INVALID");
        });
        hypotheses.values().forEach(hypothesis -> {
            sameInvestigation(hypothesis.investigationId());
            if (hypothesis.parentId() != null) {
                require(hypotheses, hypothesis.parentId(), "AUTHORITY_SNAPSHOT_HYPOTHESIS_PARENT_INVALID");
            }
        });
        toolUses.values().forEach(toolUse -> {
            sameInvestigation(toolUse.investigationId());
            require(runs, toolUse.runId(), "AUTHORITY_SNAPSHOT_TOOL_RUN_INVALID");
        });
        if (conclusion != null) {
            sameInvestigation(conclusion.investigationId());
            conclusion.supportingObservationIds().forEach(id ->
                    require(observations, id, "AUTHORITY_SNAPSHOT_CONCLUSION_EVIDENCE_INVALID"));
        }
        audit.forEach(record -> {
            sameInvestigation(record.investigationId());
            if (record.aggregateVersion() < 0 || record.aggregateVersion() > version) {
                throw new IllegalArgumentException("AUTHORITY_SNAPSHOT_AUDIT_VERSION_INVALID");
            }
        });
    }

    private void sameInvestigation(AuthorityId candidate) {
        if (!investigationId.equals(candidate)) {
            throw new IllegalArgumentException("AUTHORITY_SNAPSHOT_OWNERSHIP_INVALID");
        }
    }

    private static boolean hypothesisTransitionAllowed(HypothesisStatus from, HypothesisStatus to) {
        return switch (from) {
            case PROPOSED -> to == HypothesisStatus.VALIDATING || to == HypothesisStatus.INVALIDATED;
            case VALIDATING -> to == HypothesisStatus.VALIDATED || to == HypothesisStatus.INVALIDATED
                    || to == HypothesisStatus.INCONCLUSIVE;
            default -> false;
        };
    }

    private static boolean isTerminal(InvestigationStatus value) {
        return value == InvestigationStatus.COMPLETED || value == InvestigationStatus.INCONCLUSIVE
                || value == InvestigationStatus.FAILED || value == InvestigationStatus.CANCELLED;
    }

    private static Map<InvestigationStatus, Set<InvestigationStatus>> transitions() {
        Map<InvestigationStatus, Set<InvestigationStatus>> result = new EnumMap<>(InvestigationStatus.class);
        result.put(InvestigationStatus.CREATED, EnumSet.of(InvestigationStatus.SCOPING));
        result.put(InvestigationStatus.SCOPING, EnumSet.of(InvestigationStatus.RESEARCHING,
                InvestigationStatus.WAITING_FOR_HUMAN, InvestigationStatus.SYNTHESIZING,
                InvestigationStatus.INCONCLUSIVE, InvestigationStatus.CANCELLED, InvestigationStatus.FAILED));
        result.put(InvestigationStatus.RESEARCHING, EnumSet.of(InvestigationStatus.FORMING_HYPOTHESES,
                InvestigationStatus.WAITING_FOR_HUMAN, InvestigationStatus.SYNTHESIZING,
                InvestigationStatus.INCONCLUSIVE, InvestigationStatus.CANCELLED, InvestigationStatus.FAILED));
        result.put(InvestigationStatus.FORMING_HYPOTHESES, EnumSet.of(InvestigationStatus.VALIDATING,
                InvestigationStatus.WAITING_FOR_HUMAN, InvestigationStatus.SYNTHESIZING,
                InvestigationStatus.INCONCLUSIVE, InvestigationStatus.CANCELLED));
        result.put(InvestigationStatus.VALIDATING, EnumSet.of(InvestigationStatus.SYNTHESIZING,
                InvestigationStatus.WAITING_FOR_HUMAN, InvestigationStatus.INCONCLUSIVE,
                InvestigationStatus.CANCELLED));
        result.put(InvestigationStatus.WAITING_FOR_HUMAN, EnumSet.of(InvestigationStatus.RESEARCHING,
                InvestigationStatus.VALIDATING, InvestigationStatus.SYNTHESIZING, InvestigationStatus.CANCELLED));
        result.put(InvestigationStatus.SYNTHESIZING, EnumSet.of(InvestigationStatus.COMPLETED,
                InvestigationStatus.INCONCLUSIVE, InvestigationStatus.FAILED));
        return Map.copyOf(result);
    }

    private static String required(String value, int maxLength) {
        String normalized = Objects.requireNonNull(value, "value").strip();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException("AUTHORITY_VALUE_INVALID");
        }
        return normalized;
    }

    private static String bounded(String value, int maxLength) {
        return required(value, maxLength);
    }

    private static String requiredCode(String value) {
        String normalized = required(value, 64);
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("AUTHORITY_CODE_INVALID");
        }
        return normalized;
    }

    private static String reason(String value) {
        return value == null ? null : requiredCode(value);
    }

    private static String sha256(String value) {
        String normalized = required(value, 64).toLowerCase();
        if (!SHA256.matcher(normalized).matches()) {
            throw new IllegalArgumentException("AUTHORITY_SHA256_INVALID");
        }
        return normalized;
    }

    private static List<String> argumentNames(List<String> values) {
        List<String> source = values == null ? List.of() : List.copyOf(values);
        if (source.size() > 64) {
            throw new IllegalArgumentException("AUTHORITY_TOOL_ARGUMENT_METADATA_TOO_LARGE");
        }
        return source.stream().map(value -> {
            String normalized = required(value, 64);
            if (!ARGUMENT_NAME.matcher(normalized).matches() || SENSITIVE_NAME.matcher(normalized).matches()) {
                throw new IllegalArgumentException("AUTHORITY_TOOL_ARGUMENT_METADATA_UNSAFE");
            }
            return normalized;
        }).distinct().sorted().toList();
    }

    private static int argumentSize(int value) {
        if (value < 0 || value > 65_536) {
            throw new IllegalArgumentException("AUTHORITY_TOOL_ARGUMENT_SIZE_INVALID");
        }
        return value;
    }

    private static String targetScope(String value) {
        String normalized = required(value, 256);
        if (!SAFE_SCOPE.matcher(normalized).matches() || SENSITIVE_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("AUTHORITY_TOOL_SCOPE_UNSAFE");
        }
        return normalized;
    }

    private static List<EvidenceReference> evidenceReferences(List<EvidenceReference> values) {
        List<EvidenceReference> source = values == null ? List.of() : List.copyOf(values);
        if (source.size() > 64 || source.stream().distinct().count() != source.size()) {
            throw new IllegalArgumentException("AUTHORITY_TOOL_EVIDENCE_REFERENCES_INVALID");
        }
        return source;
    }

    private static void validateToolOutcome(ToolUseStatus status, String reasonCode,
            List<EvidenceReference> evidenceReferences) {
        if (status == ToolUseStatus.SUCCEEDED) {
            if (reasonCode != null || evidenceReferences.isEmpty()) {
                throw new IllegalArgumentException("AUTHORITY_TOOL_SUCCESS_EVIDENCE_REQUIRED");
            }
        } else if (reasonCode == null) {
            throw new IllegalArgumentException("AUTHORITY_TOOL_REASON_REQUIRED");
        } else if (!evidenceReferences.isEmpty()) {
            throw new IllegalArgumentException("AUTHORITY_TOOL_MISSING_EVIDENCE_MUST_NOT_BE_FABRICATED");
        }
    }

    private static List<String> boundedStrings(List<String> values, int maxItems, int maxLength) {
        List<String> source = values == null ? List.of() : values;
        if (source.size() > maxItems) {
            throw new IllegalArgumentException("AUTHORITY_LIST_TOO_LARGE");
        }
        return source.stream().map(value -> required(value, maxLength)).distinct().toList();
    }

    private static <T> T require(Map<AuthorityId, T> values, AuthorityId id, String code) {
        T value = values.get(Objects.requireNonNull(id, "id"));
        if (value == null) {
            throw new IllegalArgumentException(code);
        }
        return value;
    }

    private static Map<AuthorityId, RunState> index(List<RunState> values) {
        Map<AuthorityId, RunState> result = new LinkedHashMap<>();
        values.forEach(value -> duplicateSafe(result, value.id(), value));
        return result;
    }

    private static Map<AuthorityId, StepState> indexSteps(List<StepState> values) {
        Map<AuthorityId, StepState> result = new LinkedHashMap<>();
        values.forEach(value -> duplicateSafe(result, value.id(), value));
        return result;
    }

    private static Map<AuthorityId, ObservationState> indexObservations(List<ObservationState> values) {
        Map<AuthorityId, ObservationState> result = new LinkedHashMap<>();
        values.forEach(value -> duplicateSafe(result, value.id(), value));
        return result;
    }

    private static Map<AuthorityId, HypothesisState> indexHypotheses(List<HypothesisState> values) {
        Map<AuthorityId, HypothesisState> result = new LinkedHashMap<>();
        values.forEach(value -> duplicateSafe(result, value.id(), value));
        return result;
    }

    private static Map<AuthorityId, ToolUseState> indexToolUses(List<ToolUseState> values) {
        Map<AuthorityId, ToolUseState> result = new LinkedHashMap<>();
        values.forEach(value -> duplicateSafe(result, value.id(), value));
        return result;
    }

    private static <T> void duplicateSafe(Map<AuthorityId, T> target, AuthorityId id, T value) {
        if (target.put(id, value) != null) {
            throw new IllegalArgumentException("AUTHORITY_SNAPSHOT_DUPLICATE_ID");
        }
    }

    /** Incident 的冻结权威事实。 */
    public record IncidentState(AuthorityId id, String serviceCode, String environment, String releaseVersion,
                                String commitSha, String symptom, Instant createdAt) {
        /** 校验 Incident 事实。 */
        public IncidentState {
            Objects.requireNonNull(id, "id");
            serviceCode = required(serviceCode, 128);
            environment = required(environment, 64);
            releaseVersion = required(releaseVersion, 128);
            commitSha = required(commitSha, 128);
            symptom = bounded(symptom, 2048);
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    /** 可恢复的预算上限。 */
    public record BudgetPolicy(int maxSteps, int maxToolCalls, Duration maxDuration, int maxNoProgressRounds) {
        /** 校验预算。 */
        public BudgetPolicy {
            if (maxSteps < 1 || maxSteps > 10_000 || maxToolCalls < 1 || maxToolCalls > 10_000
                    || maxNoProgressRounds < 1 || maxNoProgressRounds > 1_000
                    || Objects.requireNonNull(maxDuration, "maxDuration").isNegative() || maxDuration.isZero()) {
                throw new IllegalArgumentException("AUTHORITY_BUDGET_INVALID");
            }
        }
    }

    /** 单次运行的不可变组件绑定和结束时间。 */
    public record RunState(AuthorityId id, AuthorityId investigationId, String modelVersion, String promptVersion,
                           String toolsetVersion, Instant startedAt, Instant endedAt) {
        private RunState ended(Instant at) {
            if (endedAt != null || at.isBefore(startedAt)) {
                throw new IllegalStateException("AUTHORITY_RUN_END_INVALID");
            }
            return new RunState(id, investigationId, modelVersion, promptVersion, toolsetVersion, startedAt, at);
        }
    }

    /** 仅追加的步骤。 */
    public record StepState(AuthorityId id, AuthorityId investigationId, AuthorityId runId, int order,
                            String type, String summary, Instant createdAt) {
    }

    /** 只含引用、摘要哈希和安全摘要的 Observation。 */
    public record ObservationState(AuthorityId id, AuthorityId investigationId, AuthorityId runId,
                                   AuthorityId stepId, String source, String evidenceReference,
                                   String evidenceSha256, String summary, Instant createdAt) {
    }

    /** 稳定身份、可修订状态的 Hypothesis。 */
    public record HypothesisState(AuthorityId id, AuthorityId investigationId, AuthorityId parentId,
                                  String description, HypothesisStatus status, Instant createdAt, Instant updatedAt) {
        private HypothesisState revised(HypothesisStatus target, Instant at) {
            return new HypothesisState(id, investigationId, parentId, description, target, createdAt, at);
        }
    }

    /** ToolUse 的安全状态。 */
    public record ToolUseState(AuthorityId id, AuthorityId investigationId, AuthorityId runId, String toolName,
                               String contractVersion, String argumentSha256, List<String> argumentNames,
                               int argumentSizeBytes, String targetScope, String correlationId,
                               ToolUseStatus status, String reasonCode, List<EvidenceReference> evidenceReferences,
                               Instant occurredAt) {
        /** 恢复时重新执行与写入命令相同的安全校验。 */
        public ToolUseState {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(investigationId, "investigationId");
            Objects.requireNonNull(runId, "runId");
            toolName = required(toolName, 128);
            contractVersion = required(contractVersion, 128);
            argumentSha256 = sha256(argumentSha256);
            argumentNames = InvestigationAuthority.argumentNames(argumentNames);
            argumentSizeBytes = argumentSize(argumentSizeBytes);
            targetScope = InvestigationAuthority.targetScope(targetScope);
            correlationId = required(correlationId, 128);
            Objects.requireNonNull(status, "status");
            reasonCode = reason(reasonCode);
            evidenceReferences = InvestigationAuthority.evidenceReferences(evidenceReferences);
            validateToolOutcome(status, reasonCode, evidenceReferences);
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    /** ToolUse 写入命令。 */
    public record ToolUseCommand(String toolName, String contractVersion, String argumentSha256,
                                 List<String> argumentNames, int argumentSizeBytes, String targetScope,
                                 String correlationId, ToolUseStatus status, String reasonCode,
                                 List<EvidenceReference> evidenceReferences) {
        /** 拒绝凭据元数据、原始包体形态和伪造的缺失证据。 */
        public ToolUseCommand {
            toolName = required(toolName, 128);
            contractVersion = required(contractVersion, 128);
            argumentSha256 = sha256(argumentSha256);
            argumentNames = InvestigationAuthority.argumentNames(argumentNames);
            argumentSizeBytes = argumentSize(argumentSizeBytes);
            targetScope = InvestigationAuthority.targetScope(targetScope);
            correlationId = required(correlationId, 128);
            Objects.requireNonNull(status, "status");
            reasonCode = reason(reasonCode);
            evidenceReferences = InvestigationAuthority.evidenceReferences(evidenceReferences);
            validateToolOutcome(status, reasonCode, evidenceReferences);
        }
    }

    /** DPOMBase 返回的不可变证据清单引用，不含证据正文。 */
    public record EvidenceReference(String evidenceId, String sourceCapability, String sourceAdapter,
                                    String artifactRef, String sha256) {
        /** 校验稳定身份、来源、对象引用和内容摘要。 */
        public EvidenceReference {
            evidenceId = required(evidenceId, 128);
            sourceCapability = requiredCode(sourceCapability);
            sourceAdapter = required(sourceAdapter, 128);
            artifactRef = targetScope(artifactRef);
            sha256 = InvestigationAuthority.sha256(sha256);
        }
    }

    /** 终态结论。 */
    public record ConclusionState(AuthorityId id, AuthorityId investigationId, ConclusionDisposition disposition,
                                  String rootCause, List<AuthorityId> supportingObservationIds,
                                  List<String> alternatives, List<String> evidenceGaps, Instant createdAt) {
    }

    /** 追加审计。 */
    public record AuditRecord(AuthorityId id, AuthorityId investigationId, long sequence, long aggregateVersion,
                              AuditKind kind, AuthorityId entityId, String reasonCode, Instant occurredAt) {
    }

    /** 完整不可变快照。 */
    public record Snapshot(IncidentState incident, AuthorityId investigationId, long version,
                           InvestigationStatus status, AuthorityId currentRunId, BudgetPolicy budget,
                           int stepsUsed, int toolCallsUsed, int noProgressRounds, Instant createdAt,
                           Instant updatedAt, List<RunState> runs, List<StepState> steps,
                           List<ObservationState> observations, List<HypothesisState> hypotheses,
                           List<ToolUseState> toolUses, ConclusionState conclusion, List<AuditRecord> audit) {
    }

    /** ToolUse 终态。 */
    public enum ToolUseStatus {
        /** 成功且返回受限引用/摘要。 */
        SUCCEEDED,
        /** 工具执行失败。 */
        FAILED,
        /** 上游不可用。 */
        UNAVAILABLE,
        /** 范围、策略或授权拒绝。 */
        REJECTED
    }

    /** 结论强度。 */
    public enum ConclusionDisposition {
        /** 证据确认。 */
        CONFIRMED,
        /** 仍为假设。 */
        HYPOTHESIS,
        /** 证据不足。 */
        UNDETERMINED
    }

    /** 固定低基数审计类型。 */
    public enum AuditKind {
        /** 创建调查。 */
        INVESTIGATION_CREATED,
        /** 状态变化。 */
        STATUS_CHANGED,
        /** Run 开始。 */
        RUN_STARTED,
        /** Run 结束。 */
        RUN_ENDED,
        /** 步骤追加。 */
        STEP_APPENDED,
        /** Observation 追加。 */
        OBSERVATION_APPENDED,
        /** Hypothesis 提出。 */
        HYPOTHESIS_PROPOSED,
        /** Hypothesis 修订。 */
        HYPOTHESIS_REVISED,
        /** ToolUse 记录。 */
        TOOL_USE_RECORDED,
        /** 预算更新。 */
        BUDGET_UPDATED,
        /** 结论提交。 */
        CONCLUSION_COMMITTED
    }
}
