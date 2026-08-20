package com.dpom.agent.core.alarm;

import com.dpom.agent.common.alarm.HandlingArtifactPort;
import com.dpom.agent.common.alarm.HandlingArtifactRequest;
import com.dpom.agent.common.alarm.HandlingArtifactResult;
import com.dpom.agent.core.script.ApprovalStatus;
import com.dpom.agent.core.script.ScriptArtifact;
import com.dpom.agent.core.script.ScriptArtifactService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 处置工件生成端口实现：委托 agent-core 既有 {@link ScriptArtifactService} 生成脚本工件。
 *
 * <p>仅生成工件（修复脚本带 {@code REQUIRES_APPROVAL}），不执行任何生产操作、不持有凭据。</p>
 */
@Component
public class HandlingArtifactAdapter implements HandlingArtifactPort {

    private static final Logger LOG = LoggerFactory.getLogger(HandlingArtifactAdapter.class);

    private final ScriptArtifactService scriptArtifactService;

    /**
     * 构造处置工件适配器。
     *
     * @param scriptArtifactService 脚本工件服务
     */
    public HandlingArtifactAdapter(ScriptArtifactService scriptArtifactService) {
        this.scriptArtifactService = scriptArtifactService;
    }

    @Override
    public HandlingArtifactResult generate(HandlingArtifactRequest request) {
        ScriptArtifact artifact = scriptArtifactService.createMitigation(
                request.investigationId(), null, null, null, request.language(), request.purpose(),
                request.riskLevel(), request.preconditions(), request.verification(), request.rollback(),
                request.content());
        LOG.info("处置工件已生成 artifactId={} investigationId={} approvalStatus={}", artifact.id(),
                request.investigationId(), artifact.approvalStatus());
        return new HandlingArtifactResult(artifact.id(),
                ApprovalStatus.REQUIRES_APPROVAL.name());
    }
}
