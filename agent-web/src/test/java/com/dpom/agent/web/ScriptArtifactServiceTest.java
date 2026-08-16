package com.dpom.agent.web;

import com.dpom.agent.core.persistence.command.IncidentInsert;
import com.dpom.agent.core.persistence.command.InvestigationInsert;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.observation.Observation;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.ObservationDao;
import com.dpom.agent.core.script.ApprovalStatus;
import com.dpom.agent.core.script.ScriptArtifact;
import com.dpom.agent.core.script.ScriptArtifactService;
import com.dpom.agent.core.script.ScriptPolicyViolation;
import com.dpom.agent.core.script.ScriptResultService;
import com.dpom.agent.core.script.ScriptType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 脚本工件服务验收：安全脚本可生成、UPDATE 型只读被拒绝、回传结果推动 WAITING_FOR_HUMAN→RESEARCHING。
 */
@SpringBootTest
class ScriptArtifactServiceTest {

    @Autowired
    private IncidentDao incidentDao;

    @Autowired
    private InvestigationDao investigationDao;

    @Autowired
    private ObservationDao observationDao;

    @Autowired
    private ScriptArtifactService scriptArtifactService;

    @Autowired
    private ScriptResultService scriptResultService;

    /**
     * 安全脚本可生成、UPDATE 型被拒绝、回传结果恢复调查。
     */
    @Test
    void generatesSafeScriptRejectsMutationAndResumesInvestigation() {
        long investigationId = createInvestigation();

        ScriptArtifact safe = scriptArtifactService.create(investigationId, ScriptType.READ_ONLY_DIAGNOSTIC,
                "sql", "验证 INSERT 是否执行", "LOW", null, "对比行数", null,
                "SELECT count(*) FROM asset", "INSERT 后事务回滚", "行数不变", "在只读库执行");
        assertThat(safe.readOnly()).isTrue();
        assertThat(safe.approvalStatus()).isEqualTo(ApprovalStatus.NONE_REQUIRED.name());
        assertThat(safe.hypothesesToValidate()).isEqualTo("INSERT 后事务回滚");

        assertThatThrownBy(() -> scriptArtifactService.create(investigationId, ScriptType.READ_ONLY_DIAGNOSTIC,
                "sql", "修复", "LOW", null, null, null, "UPDATE asset SET name='x'",
                null, null, null))
                .isInstanceOf(ScriptPolicyViolation.class);

        investigationDao.updateStatus(investigationId, InvestigationStatus.WAITING_FOR_HUMAN);
        long observationId = scriptResultService.submitResult(investigationId, safe.id(), "行数未变，未回滚");

        assertThat(investigationDao.findById(investigationId).orElseThrow().status())
                .isEqualTo(InvestigationStatus.RESEARCHING);
        List<Observation> observations = observationDao.findByInvestigationId(investigationId);
        assertThat(observations).anyMatch(o -> o.id().equals(observationId) && "script".equals(o.source()));
    }

    /**
     * 创建调查。
     */
    private long createInvestigation() {
        IncidentInsert incidentCommand = new IncidentInsert("asset-service", "prod", "1.0.0", "abc123", "症状");
        incidentDao.insert(incidentCommand);
        InvestigationInsert investigationCommand = new InvestigationInsert(incidentCommand.getId(),
                InvestigationStatus.CREATED, null, 50, 100, 1800, 5);
        investigationDao.insert(investigationCommand);
        return investigationCommand.getId();
    }
}
