package com.dpom.agent.alarm.governance;

import com.dpom.agent.alarm.persistence.AlarmAuditDao;
import com.dpom.agent.alarm.persistence.command.AlarmAuditInsert;
import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 严重度分级器：可配置映射表，版本可追溯、变更可审计。
 *
 * <p>默认规则与各来源标准化器一致；可通过 {@link #replaceRules} 在运行时替换并写审计。</p>
 */
@Service
public class SeverityGrader {

    private static final String DEFAULT_VERSION = "default-v1";

    private final AlarmAuditDao auditDao;
    private Map<String, SeverityLevel> table;
    private String version;

    /**
     * 构造分级器并装入默认规则。
     *
     * @param auditDao 审计持久化
     */
    public SeverityGrader(AlarmAuditDao auditDao) {
        this.auditDao = auditDao;
        this.table = toTable(defaultRules());
        this.version = DEFAULT_VERSION;
    }

    /**
     * 按来源与原始严重度分级。
     *
     * @param source      来源服务
     * @param rawSeverity 原始严重度（可为空）
     * @return 统一严重度
     */
    public SeverityLevel grade(AlarmSource source, String rawSeverity) {
        if (rawSeverity == null) {
            return SeverityLevel.WARNING;
        }
        return table.getOrDefault(key(source, rawSeverity), SeverityLevel.WARNING);
    }

    /**
     * 返回当前规则版本。
     *
     * @return 版本
     */
    public String version() {
        return version;
    }

    /**
     * 替换分级规则并写审计。
     *
     * @param newRules 新规则
     * @param newVersion 新版本
     * @param operator  操作人
     */
    public void replaceRules(List<SeverityGradingRule> newRules, String newVersion, String operator) {
        this.table = toTable(newRules);
        this.version = newVersion;
        auditDao.insert(new AlarmAuditInsert("GRADING_UPDATE", "CONFIG", null, operator,
                "version=" + newVersion, "OK"));
    }

    private static String key(AlarmSource source, String rawSeverity) {
        return source.name() + "|" + rawSeverity.toUpperCase();
    }

    private static Map<String, SeverityLevel> toTable(List<SeverityGradingRule> rules) {
        Map<String, SeverityLevel> map = new HashMap<>();
        for (SeverityGradingRule rule : rules) {
            map.put(key(rule.source(), rule.rawSeverity()), rule.unified());
        }
        return map;
    }

    private static List<SeverityGradingRule> defaultRules() {
        return List.of(
                new SeverityGradingRule(AlarmSource.AOM, "Critical", SeverityLevel.CRITICAL),
                new SeverityGradingRule(AlarmSource.AOM, "Major", SeverityLevel.CRITICAL),
                new SeverityGradingRule(AlarmSource.AOM, "Minor", SeverityLevel.WARNING),
                new SeverityGradingRule(AlarmSource.CES, "Urgent", SeverityLevel.CRITICAL),
                new SeverityGradingRule(AlarmSource.CES, "Critical", SeverityLevel.CRITICAL),
                new SeverityGradingRule(AlarmSource.APM, "Fatal", SeverityLevel.CRITICAL),
                new SeverityGradingRule(AlarmSource.APM, "Error", SeverityLevel.CRITICAL),
                new SeverityGradingRule(AlarmSource.LTS, "Error", SeverityLevel.CRITICAL));
    }
}
