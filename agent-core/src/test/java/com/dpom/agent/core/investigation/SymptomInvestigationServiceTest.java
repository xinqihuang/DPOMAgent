package com.dpom.agent.core.investigation;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.dpom.agent.common.runtime.RuntimeEvidenceClient;
import com.dpom.agent.core.cache.SnapshotCache;
import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.workspace.CodeWorkspace;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 症状调查编排服务单测：装配真实客户端并委托协调器。
 */
class SymptomInvestigationServiceTest {

    /**
     * 运行调查：解析快照、构建执行器与大脑、委托协调器。
     */
    @Test
    void wiresClientsAndDelegatesToCoordinator() {
        ModelClient modelClient = mock(ModelClient.class);
        CodeGraphClient codeGraphClient = mock(CodeGraphClient.class);
        CodeWorkspace workspace = new CodeWorkspace();
        RuntimeEvidenceClient runtimeClient = mock(RuntimeEvidenceClient.class);
        LogTemplateMinerClient logTemplateMinerClient = mock(LogTemplateMinerClient.class);
        SnapshotCache snapshotCache = mock(SnapshotCache.class);
        InvestigationCoordinator coordinator = mock(InvestigationCoordinator.class);
        InvestigationDao investigationDao = mock(InvestigationDao.class);
        IncidentDao incidentDao = mock(IncidentDao.class);

        long investigationId = 7L;
        long incidentId = 3L;
        when(investigationDao.findById(investigationId)).thenReturn(Optional.of(new Investigation(
                investigationId, incidentId, InvestigationStatus.CREATED, null, 50, 100, 1800, 5, null, null)));
        when(incidentDao.findById(incidentId)).thenReturn(Optional.of(new Incident(
                incidentId, "asset-service", "prod", "1.0.0", "abc123", "症状", null)));
        when(codeGraphClient.resolveSnapshot("asset-service", "abc123"))
                .thenReturn(new CodeSnapshot("snap-1", "asset-service", "abc123", "/repos/asset-service", SnapshotStatus.READY));

        when(snapshotCache.get("asset-service", "abc123")).thenReturn(Optional.empty());
        new SymptomInvestigationService(modelClient, codeGraphClient, workspace, runtimeClient,
                logTemplateMinerClient, snapshotCache, coordinator, investigationDao, incidentDao).run(investigationId);

        verify(codeGraphClient).resolveSnapshot("asset-service", "abc123");
        verify(coordinator).run(eq(investigationId), any(Brain.class), any(ToolExecutor.class));
    }
}
