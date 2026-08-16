package com.dpom.agent.core.investigation;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.dpom.agent.common.runtime.RuntimeEvidenceClient;
import com.dpom.agent.core.cache.SnapshotCache;
import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.tool.InvestigationToolExecutor;
import com.dpom.agent.core.workspace.CodeWorkspace;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * 症状调查编排服务：把真实 LLM（DeepSeek）与 MCP 客户端（CodeGraph/Drain3）装配进调查循环。
 */
@Service
public class SymptomInvestigationService {

    private final ModelClient modelClient;
    private final CodeGraphClient codeGraphClient;
    private final CodeWorkspace workspace;
    private final RuntimeEvidenceClient runtimeClient;
    private final LogTemplateMinerClient logTemplateMinerClient;
    private final SnapshotCache snapshotCache;
    private final InvestigationCoordinator coordinator;
    private final InvestigationDao investigationDao;
    private final IncidentDao incidentDao;

    /**
     * 构造器注入。
     *
     * @param modelClient           模型客户端（DeepSeek）
     * @param codeGraphClient       代码图客户端（CodeGraph stdio MCP）
     * @param workspace             代码工作区
     * @param runtimeClient         运行时证据客户端
     * @param logTemplateMinerClient 日志模板挖掘客户端（Drain3 MCP）
     * @param snapshotCache          快照缓存（Redis）
     * @param coordinator           调查协调器
     * @param investigationDao      调查 DAO
     * @param incidentDao           事件 DAO
     */
    public SymptomInvestigationService(ModelClient modelClient, CodeGraphClient codeGraphClient,
                                       CodeWorkspace workspace, RuntimeEvidenceClient runtimeClient,
                                       LogTemplateMinerClient logTemplateMinerClient, SnapshotCache snapshotCache,
                                       InvestigationCoordinator coordinator, InvestigationDao investigationDao,
                                       IncidentDao incidentDao) {
        this.modelClient = modelClient;
        this.codeGraphClient = codeGraphClient;
        this.workspace = workspace;
        this.runtimeClient = runtimeClient;
        this.logTemplateMinerClient = logTemplateMinerClient;
        this.snapshotCache = snapshotCache;
        this.coordinator = coordinator;
        this.investigationDao = investigationDao;
        this.incidentDao = incidentDao;
    }

    /**
     * 运行症状驱动调查（无堆栈场景）。
     *
     * @param investigationId 调查 id
     */
    public void run(long investigationId) {
        Investigation investigation = investigationDao.findById(investigationId)
                .orElseThrow(() -> new IllegalArgumentException("调查不存在：" + investigationId));
        Incident incident = incidentDao.findById(investigation.incidentId())
                .orElseThrow(() -> new IllegalArgumentException("事件不存在：" + investigation.incidentId()));

        CodeSnapshot snapshot = snapshotCache.get(incident.serviceCode(), incident.commitSha())
                .orElseGet(() -> {
                    CodeSnapshot resolved = codeGraphClient.resolveSnapshot(incident.serviceCode(), incident.commitSha());
                    snapshotCache.put(resolved);
                    return resolved;
                });
        InvestigationToolExecutor executor = new InvestigationToolExecutor(
                snapshot.snapshotId(), Path.of(snapshot.workspacePath()), incident.serviceCode(), incident.environment(),
                codeGraphClient, workspace, runtimeClient, logTemplateMinerClient);
        SymptomBrain brain = new SymptomBrain(modelClient, incident.symptom());
        coordinator.run(investigationId, brain, executor);
    }
}
