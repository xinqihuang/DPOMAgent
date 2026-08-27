package com.dpom.agent.core.authority;

import com.dpom.agent.core.authority.InvestigationAuthority.BudgetPolicy;
import com.dpom.agent.core.authority.InvestigationAuthority.ConclusionDisposition;
import com.dpom.agent.core.authority.InvestigationAuthority.IncidentState;
import com.dpom.agent.core.authority.InvestigationAuthority.ToolUseCommand;
import com.dpom.agent.core.authority.InvestigationAuthority.ToolUseStatus;
import com.dpom.agent.core.hypothesis.HypothesisStatus;
import com.dpom.agent.core.investigation.InvestigationStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Investigation 权威聚合测试。 */
class InvestigationAuthorityTest {

    private static final Instant START = Instant.parse("2026-08-27T00:00:00Z");

    @Test
    void identitiesAreDeterministicAndNamespaceSeparated() {
        assertThat(AuthorityId.derive("incident", "source-1"))
                .isEqualTo(AuthorityId.derive("incident", "source-1"))
                .isNotEqualTo(AuthorityId.derive("run", "source-1"));
    }

    @Test
    void completeLifecycleRestoresExactlyWithOrderedAudit() {
        InvestigationAuthority authority = authority(4, 3);
        authority.transition(0, InvestigationStatus.SCOPING, at(1));
        authority.transition(1, InvestigationStatus.RESEARCHING, at(2));
        authority.startRun(2, "deepseek@1", "prompt@1", "tools@1", at(3));
        AuthorityId step = authority.appendStep(3, "EVIDENCE_COLLECTION", "collect bounded evidence", at(4));
        AuthorityId observation = authority.appendObservation(4, step, "dpom-base", "obs://evidence/1",
                "a".repeat(64), "trace supports the candidate", at(5));
        authority.transition(5, InvestigationStatus.FORMING_HYPOTHESES, at(6));
        AuthorityId hypothesis = authority.proposeHypothesis(6, null, "deployment caused rollback pressure", at(7));
        authority.transition(7, InvestigationStatus.VALIDATING, at(8));
        authority.reviseHypothesis(8, hypothesis, HypothesisStatus.VALIDATING, at(9));
        authority.reviseHypothesis(9, hypothesis, HypothesisStatus.VALIDATED, at(10));
        authority.recordToolUse(10, new ToolUseCommand("query_traces", "1.0", "b".repeat(64),
                List.of("from", "to"), 128, "huawei:cn-north-9:apm:2121291",
                "tool-call-1", ToolUseStatus.SUCCEEDED, null, List.of(evidence())), at(11));
        authority.transition(11, InvestigationStatus.SYNTHESIZING, at(12));
        authority.endRun(12, at(13));
        authority.conclude(13, ConclusionDisposition.CONFIRMED, "deployment rollback pressure",
                List.of(observation), List.of("cache saturation"), List.of(), at(14));

        var snapshot = authority.snapshot();
        var restored = InvestigationAuthority.restore(snapshot).snapshot();

        assertThat(restored).isEqualTo(snapshot);
        assertThat(restored.status()).isEqualTo(InvestigationStatus.COMPLETED);
        assertThat(restored.audit()).extracting(InvestigationAuthority.AuditRecord::sequence)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L);
        assertThat(restored.audit().get(restored.audit().size() - 1).aggregateVersion()).isEqualTo(14L);
    }

    @Test
    void staleVersionDoesNotMutateState() {
        InvestigationAuthority authority = authority(2, 2);
        authority.transition(0, InvestigationStatus.SCOPING, at(1));
        var before = authority.snapshot();

        assertThatThrownBy(() -> authority.transition(0, InvestigationStatus.RESEARCHING, at(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AUTHORITY_VERSION_CONFLICT");
        assertThat(authority.snapshot()).isEqualTo(before);
    }

    @Test
    void confirmedConclusionRequiresKnownEvidence() {
        InvestigationAuthority authority = authority(2, 2);
        authority.transition(0, InvestigationStatus.SCOPING, at(1));
        authority.transition(1, InvestigationStatus.SYNTHESIZING, at(2));

        assertThatThrownBy(() -> authority.conclude(2, ConclusionDisposition.CONFIRMED, "unsupported",
                List.of(), List.of(), List.of("trace missing"), at(3)))
                .hasMessage("AUTHORITY_CONFIRMED_EVIDENCE_REQUIRED");
        assertThat(authority.snapshot().conclusion()).isNull();
    }

    @Test
    void toolBudgetAndStableFailureReasonsFailClosed() {
        InvestigationAuthority authority = authority(2, 1);
        authority.transition(0, InvestigationStatus.SCOPING, at(1));
        authority.startRun(1, "model@1", "prompt@1", "tools@1", at(2));
        authority.recordToolUse(2, new ToolUseCommand("query_traces", "1.0", "c".repeat(64),
                List.of("from", "to"), 128, "huawei:cn-north-9:apm:2121291",
                "tool-call-1", ToolUseStatus.UNAVAILABLE, "UPSTREAM_TIMEOUT", List.of()), at(3));

        assertThatThrownBy(() -> authority.recordToolUse(3, new ToolUseCommand("query_metrics", "1.0",
                "d".repeat(64), List.of("metric"), 64, "huawei:cn-north-9:apm:2121291",
                "tool-call-2", ToolUseStatus.SUCCEEDED, null, List.of(evidence())), at(4)))
                .hasMessage("AUTHORITY_TOOL_BUDGET_EXHAUSTED");
        assertThatThrownBy(() -> new ToolUseCommand("query_metrics", "1.0", "d".repeat(64),
                List.of("metric"), 64, "huawei:cn-north-9:apm:2121291",
                "tool-call-3", ToolUseStatus.FAILED, null, List.of()))
                .hasMessage("AUTHORITY_TOOL_REASON_REQUIRED");
    }

    @Test
    void toolUseRejectsCredentialMetadataRawBodiesAndFabricatedMissingEvidence() {
        assertThatThrownBy(() -> new ToolUseCommand("query_metrics", "1.0", "d".repeat(64),
                List.of("accessKey"), 64, "huawei:cn-north-9:apm:2121291",
                "tool-call-1", ToolUseStatus.SUCCEEDED, null, List.of(evidence())))
                .hasMessage("AUTHORITY_TOOL_ARGUMENT_METADATA_UNSAFE");
        assertThatThrownBy(() -> new ToolUseCommand("query_metrics", "1.0", "d".repeat(64),
                List.of("metric"), 64, "{\"providerEnvelope\":true}",
                "tool-call-2", ToolUseStatus.SUCCEEDED, null, List.of(evidence())))
                .hasMessage("AUTHORITY_TOOL_SCOPE_UNSAFE");
        assertThatThrownBy(() -> new ToolUseCommand("query_metrics", "1.0", "d".repeat(64),
                List.of("metric"), 64, "huawei:cn-north-9:apm:2121291",
                "tool-call-3", ToolUseStatus.UNAVAILABLE, "UPSTREAM_TIMEOUT", List.of(evidence())))
                .hasMessage("AUTHORITY_TOOL_MISSING_EVIDENCE_MUST_NOT_BE_FABRICATED");
        assertThatThrownBy(() -> new ToolUseCommand("query_metrics", "1.0", "d".repeat(64),
                List.of("metric"), 65_537, "huawei:cn-north-9:apm:2121291",
                "tool-call-4", ToolUseStatus.SUCCEEDED, null, List.of(evidence())))
                .hasMessage("AUTHORITY_TOOL_ARGUMENT_SIZE_INVALID");
    }

    @Test
    void snapshotWithCounterDriftIsRejected() {
        var snapshot = authority(2, 2).snapshot();
        var invalid = new InvestigationAuthority.Snapshot(snapshot.incident(), snapshot.investigationId(),
                snapshot.version(), snapshot.status(), snapshot.currentRunId(), snapshot.budget(), 1,
                snapshot.toolCallsUsed(), snapshot.noProgressRounds(), snapshot.createdAt(), snapshot.updatedAt(),
                snapshot.runs(), snapshot.steps(), snapshot.observations(), snapshot.hypotheses(), snapshot.toolUses(),
                snapshot.conclusion(), snapshot.audit());

        assertThatThrownBy(() -> InvestigationAuthority.restore(invalid))
                .hasMessage("AUTHORITY_SNAPSHOT_COUNTER_INVALID");
    }

    @Test
    void exhaustedNoProgressBudgetDoesNotPartiallyMutateAggregate() {
        InvestigationAuthority authority = authority(2, 2);
        authority.transition(0, InvestigationStatus.SCOPING, at(1));
        authority.recordProgress(1, false, at(2));
        authority.recordProgress(2, false, at(3));
        var before = authority.snapshot();

        assertThatThrownBy(() -> authority.recordProgress(3, false, at(4)))
                .hasMessage("AUTHORITY_NO_PROGRESS_BUDGET_EXHAUSTED");
        assertThat(authority.snapshot()).isEqualTo(before);
    }

    private InvestigationAuthority authority(int maxSteps, int maxTools) {
        AuthorityId incidentId = AuthorityId.derive("incident", "alarm-16557989");
        IncidentState incident = new IncidentState(incidentId, "DPBinMedService", "non-production",
                "release-1", "commit-1", "CodeCache usage high", START);
        return InvestigationAuthority.create(incident, AuthorityId.derive("investigation", incidentId.value(),
                "request-1"), new BudgetPolicy(maxSteps, maxTools, Duration.ofMinutes(30), 3), START);
    }

    private Instant at(long seconds) {
        return START.plusSeconds(seconds);
    }

    private static InvestigationAuthority.EvidenceReference evidence() {
        return new InvestigationAuthority.EvidenceReference("EVID-1", "APM_TREND",
                "huawei-apm-v1", "obs://evidence/1", "e".repeat(64));
    }
}
