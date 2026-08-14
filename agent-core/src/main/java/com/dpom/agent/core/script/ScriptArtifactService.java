package com.dpom.agent.core.script;

import com.dpom.agent.core.persistence.ScriptArtifactDao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 脚本工件服务：生成并校验诊断/修复脚本（仅生成，不执行）。
 */
@Service
public class ScriptArtifactService {

    private final ScriptArtifactDao scriptArtifactDao;
    private final ScriptPolicyValidator validator;

    /**
     * 构造器注入。
     *
     * @param scriptArtifactDao 脚本 DAO
     * @param validator         策略校验器
     */
    public ScriptArtifactService(ScriptArtifactDao scriptArtifactDao, ScriptPolicyValidator validator) {
        this.scriptArtifactDao = scriptArtifactDao;
        this.validator = validator;
    }

    /**
     * 创建诊断脚本工件。
     *
     * @param investigationId       调查 id
     * @param type                  脚本类型
     * @param language              语言
     * @param purpose               用途
     * @param riskLevel             风险等级
     * @param preconditions         前置条件
     * @param verification          验证方式
     * @param rollback              回滚方案
     * @param content               脚本内容
     * @param hypothesesToValidate  待验证假设
     * @param expectedOutput        期望输出
     * @param instructions          执行说明
     * @return 脚本工件
     */
    public ScriptArtifact create(long investigationId, ScriptType type, String language, String purpose,
                                 String riskLevel, String preconditions, String verification, String rollback,
                                 String content, String hypothesesToValidate, String expectedOutput,
                                 String instructions) {
        validator.validate(type, content);
        boolean readOnly = type == ScriptType.READ_ONLY_DIAGNOSTIC;
        String approval = readOnly ? ApprovalStatus.NONE_REQUIRED.name() : ApprovalStatus.REQUIRES_APPROVAL.name();
        long id = scriptArtifactDao.insert(new ScriptArtifact(null, investigationId, type.name(), language, purpose,
                riskLevel, readOnly, approval, preconditions, verification, rollback, content,
                hypothesesToValidate, expectedOutput, instructions, null, null, null, null));
        return scriptArtifactDao.findById(id).orElseThrow();
    }

    /**
     * 创建修复脚本工件（仅生成，不执行）。
     *
     * @param investigationId 调查 id
     * @param rootCause       根因
     * @param evidenceIds     证据 id（逗号分隔）
     * @param target          修复目标
     * @param language        语言
     * @param purpose         用途
     * @param riskLevel       风险等级
     * @param preconditions   前置条件
     * @param verification    验证方式
     * @param rollback        回滚方案
     * @param content         脚本内容
     * @return 脚本工件
     */
    public ScriptArtifact createMitigation(long investigationId, String rootCause, String evidenceIds, String target,
                                           String language, String purpose, String riskLevel, String preconditions,
                                           String verification, String rollback, String content) {
        long id = scriptArtifactDao.insert(new ScriptArtifact(null, investigationId, ScriptType.MITIGATION.name(),
                language, purpose, riskLevel, false, ApprovalStatus.REQUIRES_APPROVAL.name(),
                preconditions, verification, rollback, content, null, null, null,
                rootCause, evidenceIds, target, null));
        return scriptArtifactDao.findById(id).orElseThrow();
    }

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 脚本工件（可为空）
     */
    public Optional<ScriptArtifact> findById(long id) {
        return scriptArtifactDao.findById(id);
    }

    /**
     * 按调查查询脚本工件列表。
     *
     * @param investigationId 调查 id
     * @return 脚本工件列表
     */
    public List<ScriptArtifact> findByInvestigation(long investigationId) {
        return scriptArtifactDao.findByInvestigationId(investigationId);
    }
}
