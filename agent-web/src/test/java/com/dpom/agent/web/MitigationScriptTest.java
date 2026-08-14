package com.dpom.agent.web;

import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.script.ApprovalStatus;
import com.dpom.agent.core.script.ScriptArtifact;
import com.dpom.agent.core.script.ScriptArtifactService;
import com.dpom.agent.core.script.ScriptType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 修复脚本工件验收：MITIGATION + REQUIRES_APPROVAL、含根因/证据/目标、不存在自动执行路径。
 */
@SpringBootTest
class MitigationScriptTest {

    @Autowired
    private IncidentDao incidentDao;

    @Autowired
    private InvestigationDao investigationDao;

    @Autowired
    private ScriptArtifactService scriptArtifactService;

    /**
     * 生成修复脚本工件。
     */
    @Test
    void createsMitigationArtifact() {
        long investigationId = createInvestigation();

        ScriptArtifact artifact = scriptArtifactService.createMitigation(investigationId,
                "事务回滚", "1,2", "AssetRepository.insert",
                "java", "修复事务回滚", "HIGH", "先备份", "重跑验证", "git revert", "commit();");

        assertThat(artifact.type()).isEqualTo(ScriptType.MITIGATION.name());
        assertThat(artifact.approvalStatus()).isEqualTo(ApprovalStatus.REQUIRES_APPROVAL.name());
        assertThat(artifact.readOnly()).isFalse();
        assertThat(artifact.rootCause()).isEqualTo("事务回滚");
        assertThat(artifact.evidenceIds()).isEqualTo("1,2");
        assertThat(artifact.target()).isEqualTo("AssetRepository.insert");
    }

    /**
     * 不存在自动执行路径：服务层没有 execute/exec/run 方法。
     */
    @Test
    void noAutoExecutionPath() {
        for (Method method : ScriptArtifactService.class.getDeclaredMethods()) {
            assertThat(method.getName()).doesNotContain("execute", "exec", "run");
        }
    }

    /**
     * 创建调查。
     */
    private long createInvestigation() {
        long incidentId = incidentDao.insert(new Incident(
                null, "asset-service", "prod", "1.0.0", "abc123", "症状", null));
        return investigationDao.insert(new Investigation(
                null, incidentId, InvestigationStatus.CREATED, null, 50, 100, 1800, 5, null, null));
    }
}
