package com.dpom.agent.alarm.notification;

import com.dpom.agent.alarm.domain.NotificationRule;
import com.dpom.agent.alarm.persistence.AlarmAuditDao;
import com.dpom.agent.alarm.persistence.command.AlarmAuditInsert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知编排：抑制/静默检查 → 规则匹配 → 分派发送。
 *
 * <p>抑制窗口内跳过通知并写审计；无命中规则跳过；命中则分派多渠道。</p>
 */
@Service
public class NotificationOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationOrchestrator.class);
    private static final String TARGET_TYPE = "INCIDENT";

    private final AlarmSuppressionService suppressionService;
    private final NotificationRuleMatcher matcher;
    private final NotificationDispatchService dispatch;
    private final AlarmAuditDao auditDao;

    /**
     * 构造通知编排。
     *
     * @param suppressionService 抑制服务
     * @param matcher            规则匹配引擎
     * @param dispatch           分派服务
     * @param auditDao           审计持久化
     */
    public NotificationOrchestrator(AlarmSuppressionService suppressionService, NotificationRuleMatcher matcher,
            NotificationDispatchService dispatch, AlarmAuditDao auditDao) {
        this.suppressionService = suppressionService;
        this.matcher = matcher;
        this.dispatch = dispatch;
        this.auditDao = auditDao;
    }

    /**
     * 对事件执行通知编排。
     *
     * @param incidentId 事件 id
     * @param input      匹配输入
     * @param subject    通知主题
     * @param body       通知正文
     */
    public void notify(long incidentId, NotificationMatchInput input, String subject, String body) {
        String matchKey = matchKey(input);
        if (suppressionService.isSuppressed(matchKey)) {
            auditDao.insert(new AlarmAuditInsert("NOTIFY_SKIP", TARGET_TYPE, incidentId, null,
                    "抑制/静默窗口内 matchKey=" + matchKey, "SKIPPED"));
            LOG.info("事件 {} 命中抑制窗口，跳过通知", incidentId);
            return;
        }
        List<NotificationRule> rules = matcher.match(input);
        if (rules.isEmpty()) {
            LOG.info("事件 {} 无命中通知规则，跳过", incidentId);
            return;
        }
        dispatch.dispatch(incidentId, rules, subject, body);
    }

    private static String matchKey(NotificationMatchInput input) {
        String service = input.serviceCode() == null ? "" : input.serviceCode();
        String resource = input.resourceId() == null ? "" : input.resourceId();
        return service + "|" + resource;
    }
}
