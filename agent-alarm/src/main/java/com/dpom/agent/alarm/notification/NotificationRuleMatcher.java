package com.dpom.agent.alarm.notification;

import com.dpom.agent.alarm.domain.NotificationRule;
import com.dpom.agent.alarm.persistence.NotificationRuleDao;
import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通知规则匹配引擎：按来源/服务/资源/严重度/标签匹配启用规则，多规则可同时命中（多渠道分派）。
 *
 * <p>过滤字段为空表示不限；标签过滤为 {@code k=v} 或逗号分隔多对，全部满足才命中。无命中返回空列表（跳过通知）。</p>
 */
@Service
public class NotificationRuleMatcher {

    private final NotificationRuleDao ruleDao;

    /**
     * 构造匹配引擎。
     *
     * @param ruleDao 规则持久化
     */
    public NotificationRuleMatcher(NotificationRuleDao ruleDao) {
        this.ruleDao = ruleDao;
    }

    /**
     * 匹配所有命中的启用规则。
     *
     * @param input 匹配输入
     * @return 命中规则列表（无命中返回空列表）
     */
    public List<NotificationRule> match(NotificationMatchInput input) {
        List<NotificationRule> enabled = ruleDao.findAllEnabled();
        List<NotificationRule> matched = new ArrayList<>();
        for (NotificationRule rule : enabled) {
            if (matches(rule, input)) {
                matched.add(rule);
            }
        }
        return matched;
    }

    private static boolean matches(NotificationRule rule, NotificationMatchInput input) {
        if (!sourceMatches(rule.sourceFilter(), input.source())) {
            return false;
        }
        if (!stringMatches(rule.serviceCodeFilter(), input.serviceCode())) {
            return false;
        }
        if (!stringMatches(rule.resourceFilter(), input.resourceId())) {
            return false;
        }
        if (!severityMatches(rule.severityFilter(), input.severity())) {
            return false;
        }
        return tagMatches(rule.tagFilter(), input.tags());
    }

    private static boolean sourceMatches(AlarmSource filter, AlarmSource actual) {
        return filter == null || filter == actual;
    }

    private static boolean stringMatches(String filter, String actual) {
        return filter == null || filter.equals(actual);
    }

    private static boolean severityMatches(SeverityLevel filter, SeverityLevel actual) {
        return filter == null || filter == actual;
    }

    private static boolean tagMatches(String tagFilter, Map<String, String> tags) {
        if (tagFilter == null || tagFilter.isBlank()) {
            return true;
        }
        if (tags == null || tags.isEmpty()) {
            return false;
        }
        for (String pair : tagFilter.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length != 2) {
                return false;
            }
            if (!kv[1].equals(tags.get(kv[0].trim()))) {
                return false;
            }
        }
        return true;
    }
}
