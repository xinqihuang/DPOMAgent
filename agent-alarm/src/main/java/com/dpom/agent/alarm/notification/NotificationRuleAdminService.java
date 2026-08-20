package com.dpom.agent.alarm.notification;

import com.dpom.agent.alarm.persistence.AlarmAuditDao;
import com.dpom.agent.alarm.persistence.NotificationRuleDao;
import com.dpom.agent.alarm.persistence.command.AlarmAuditInsert;
import com.dpom.agent.alarm.persistence.command.NotificationRuleInsert;
import org.springframework.stereotype.Service;

/**
 * 通知规则管理服务：新增/启停规则并写审计。
 */
@Service
public class NotificationRuleAdminService {

    private static final String TARGET_TYPE = "NOTIFICATION_RULE";

    private final NotificationRuleDao ruleDao;
    private final AlarmAuditDao auditDao;

    /**
     * 构造规则管理服务。
     *
     * @param ruleDao  规则持久化
     * @param auditDao 审计持久化
     */
    public NotificationRuleAdminService(NotificationRuleDao ruleDao, AlarmAuditDao auditDao) {
        this.ruleDao = ruleDao;
        this.auditDao = auditDao;
    }

    /**
     * 新增规则并写审计。
     *
     * @param command  插入命令
     * @param operator 操作人
     * @return 新规则 id
     */
    public long addRule(NotificationRuleInsert command, String operator) {
        ruleDao.insert(command);
        auditDao.insert(new AlarmAuditInsert("RULE_CREATE", TARGET_TYPE, command.getId(), operator,
                "name=" + command.getName(), "OK"));
        return command.getId();
    }

    /**
     * 启停规则并写审计。
     *
     * @param id       规则 id
     * @param enabled  是否启用
     * @param operator 操作人
     */
    public void setEnabled(long id, boolean enabled, String operator) {
        ruleDao.updateEnabled(id, enabled);
        auditDao.insert(new AlarmAuditInsert("RULE_TOGGLE", TARGET_TYPE, id, operator,
                "enabled=" + enabled, "OK"));
    }
}
