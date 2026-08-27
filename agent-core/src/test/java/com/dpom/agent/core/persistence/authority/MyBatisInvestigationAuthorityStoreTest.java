package com.dpom.agent.core.persistence.authority;

import com.dpom.agent.core.authority.AuthorityId;
import com.dpom.agent.core.authority.InvestigationAuthority;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** MyBatis 权威仓储的事务边界前单元契约。 */
class MyBatisInvestigationAuthorityStoreTest {

    private InvestigationAuthorityDao dao;
    private MyBatisInvestigationAuthorityStore store;

    @BeforeEach
    void setUp() {
        dao = mock(InvestigationAuthorityDao.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        store = new MyBatisInvestigationAuthorityStore(dao, objectMapper);
    }

    @Test
    void createsCanonicalSnapshotAndRestoresOnlyAfterDigestVerification() {
        InvestigationAuthority authority = authority();
        when(dao.insertHead(any())).thenReturn(1);
        when(dao.insertRevision(any())).thenReturn(1);
        when(dao.insertAudit(any())).thenReturn(1);

        store.create(authority);

        ArgumentCaptor<AuthorityHeadRow> headCaptor = ArgumentCaptor.forClass(AuthorityHeadRow.class);
        verify(dao).insertHead(headCaptor.capture());
        AuthorityHeadRow row = headCaptor.getValue();
        assertThat(row.snapshotSha256()).matches("[0-9a-f]{64}");
        assertThat(row.snapshotJson()).startsWith("{").doesNotContain("\n").doesNotContain("\r");
        when(dao.findHead(row.investigationId())).thenReturn(Optional.of(row));

        InvestigationAuthority restored = store.find(authority.snapshot().investigationId()).orElseThrow();

        assertThat(restored.snapshot()).isEqualTo(authority.snapshot());
    }

    @Test
    void rejectsDigestMismatch() {
        InvestigationAuthority authority = authority();
        when(dao.insertHead(any())).thenReturn(1);
        when(dao.insertRevision(any())).thenReturn(1);
        when(dao.insertAudit(any())).thenReturn(1);
        store.create(authority);
        ArgumentCaptor<AuthorityHeadRow> captor = ArgumentCaptor.forClass(AuthorityHeadRow.class);
        verify(dao).insertHead(captor.capture());
        AuthorityHeadRow valid = captor.getValue();
        AuthorityHeadRow tampered = new AuthorityHeadRow(valid.investigationId(), valid.incidentId(),
                valid.aggregateVersion(), valid.status(), valid.currentRunId(), valid.stepsUsed(),
                valid.toolCallsUsed(), valid.noProgressRounds(), valid.snapshotJson() + " ",
                valid.snapshotSha256(), valid.createdAt(), valid.updatedAt());
        when(dao.findHead(valid.investigationId())).thenReturn(Optional.of(tampered));

        assertThatThrownBy(() -> store.find(authority.snapshot().investigationId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AUTHORITY_SNAPSHOT_DIGEST_MISMATCH");
    }

    @Test
    void mapsZeroRowOptimisticUpdateToStableConflict() {
        InvestigationAuthority authority = authority();
        authority.transition(0L, InvestigationStatus.SCOPING, Instant.parse("2026-08-27T00:00:01Z"));
        when(dao.findMaxAuditSequence(any())).thenReturn(1L);
        when(dao.findToolUseIds(any())).thenReturn(List.of());
        when(dao.updateHead(any(), anyLong())).thenReturn(0);

        assertThatThrownBy(() -> store.save(authority, 0L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AUTHORITY_VERSION_CONFLICT");
    }

    private static InvestigationAuthority authority() {
        Instant createdAt = Instant.parse("2026-08-27T00:00:00Z");
        AuthorityId incidentId = AuthorityId.derive("incident", "alarm-16557989");
        InvestigationAuthority.IncidentState incident = new InvestigationAuthority.IncidentState(
                incidentId, "DPBinMedService", "test", "2026.08", "commit-1",
                "CodeCache pressure", createdAt);
        return InvestigationAuthority.create(incident,
                AuthorityId.derive("investigation", incidentId.value(), "attempt-1"),
                new InvestigationAuthority.BudgetPolicy(20, 20, Duration.ofMinutes(10), 3), createdAt);
    }
}
