package com.dpom.agent.alarm.notification;

import com.dpom.agent.alarm.domain.AlarmSuppression;
import com.dpom.agent.alarm.domain.SuppressionKind;
import com.dpom.agent.alarm.persistence.AlarmAuditDao;
import com.dpom.agent.alarm.persistence.AlarmSuppressionDao;
import com.dpom.agent.alarm.persistence.command.AlarmAuditInsert;
import com.dpom.agent.alarm.persistence.command.AlarmSuppressionInsert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警抑制/静默服务：创建有起止时间的抑制记录并审计；查询指定匹配键当前是否被抑制。
 *
 * <p>抑制（SUPPRESSION）按条件暂停通知，静默（SILENCE）按时间区间暂停；二者共用同一存储与查询。</p>
 */
@Service
public class AlarmSuppressionService {

    private static final Logger LOG = LoggerFactory.getLogger(AlarmSuppressionService.class);
    private static final String TARGET_TYPE = "SUPPRESSION";

    private final AlarmSuppressionDao suppressionDao;
    private final AlarmAuditDao auditDao;

    /**
     * 构造抑制服务。
     *
     * @param suppressionDao 抑制持久化
     * @param auditDao       审计持久化
     */
    public AlarmSuppressionService(AlarmSuppressionDao suppressionDao, AlarmAuditDao auditDao) {
        this.suppressionDao = suppressionDao;
        this.auditDao = auditDao;
    }

    /**
     * 创建抑制/静默记录并写审计。
     *
     * @param kind      抑制类型
     * @param matchKey  匹配键
     * @param reason    原因（可为空）
     * @param startAt   起始时间
     * @param endAt     结束时间
     * @param createdBy 创建人
     * @return 新记录 id
     */
    public long createSuppression(SuppressionKind kind, String matchKey, String reason, LocalDateTime startAt,
            LocalDateTime endAt, String createdBy) {
        AlarmSuppressionInsert command = new AlarmSuppressionInsert(kind, matchKey, reason, startAt, endAt,
                createdBy);
        suppressionDao.insert(command);
        long id = command.getId();
        auditDao.insert(new AlarmAuditInsert("SUPPRESSION_CREATE", TARGET_TYPE, id, createdBy,
                "kind=" + kind + ",matchKey=" + matchKey, "OK"));
        LOG.info("创建抑制记录 id={} kind={} matchKey={}", id, kind, matchKey);
        return id;
    }

    /**
     * 查询指定匹配键当前是否处于抑制/静默窗口内。
     *
     * @param matchKey 匹配键
     * @return 被抑制返回 true
     */
    public boolean isSuppressed(String matchKey) {
        if (matchKey == null) {
            return false;
        }
        List<AlarmSuppression> active = suppressionDao.findActiveByMatchKey(matchKey, LocalDateTime.now());
        return !active.isEmpty();
    }
}
