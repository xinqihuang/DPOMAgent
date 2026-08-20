package com.dpom.agent.alarm.notification;

import com.dpom.agent.alarm.persistence.AlarmAuditDao;
import com.dpom.agent.alarm.persistence.command.AlarmAuditInsert;
import com.dpom.agent.common.alarm.HandlingArtifactPort;
import com.dpom.agent.common.alarm.HandlingArtifactRequest;
import com.dpom.agent.common.alarm.HandlingArtifactResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 处置工件编排服务：经端口委托 agent-core 生成带 {@code REQUIRES_APPROVAL} 的脚本工件。
 *
 * <p>本服务不执行工件、不持有 AK/SK；端口未装配时安全降级（记录跳过、不抛异常）。</p>
 */
@Service
public class HandlingArtifactService {

    private static final Logger LOG = LoggerFactory.getLogger(HandlingArtifactService.class);
    private static final String TARGET_TYPE = "INCIDENT";

    private final Optional<HandlingArtifactPort> port;
    private final AlarmAuditDao auditDao;

    /**
     * 构造处置工件服务。
     *
     * @param port     处置工件端口（可为空）
     * @param auditDao 审计持久化
     */
    public HandlingArtifactService(Optional<HandlingArtifactPort> port, AlarmAuditDao auditDao) {
        this.port = port;
        this.auditDao = auditDao;
    }

    /**
     * 生成处置工件并写审计。
     *
     * @param incidentId      事件 id（仅用于审计）
     * @param investigationId 关联调查 id
     * @param language        脚本语言
     * @param purpose         用途
     * @param riskLevel       风险等级
     * @param content         脚本内容
     */
    public void generateArtifact(long incidentId, Long investigationId, String language, String purpose,
            String riskLevel, String content) {
        if (port.isEmpty()) {
            auditDao.insert(new AlarmAuditInsert("ARTIFACT_SKIP", TARGET_TYPE, incidentId, null,
                    "处置工件端口未装配，安全降级", "SKIPPED"));
            LOG.info("事件 {} 处置工件端口未装配，跳过", incidentId);
            return;
        }
        HandlingArtifactRequest request = new HandlingArtifactRequest(investigationId, "MITIGATION", language,
                purpose, riskLevel, null, null, null, content);
        HandlingArtifactResult result = port.get().generate(request);
        auditDao.insert(new AlarmAuditInsert("ARTIFACT_GENERATE", TARGET_TYPE, incidentId, null,
                "artifactId=" + result.artifactId() + ",approval=" + result.approvalStatus(), "OK"));
        LOG.info("事件 {} 处置工件已生成 artifactId={}", incidentId, result.artifactId());
    }
}
