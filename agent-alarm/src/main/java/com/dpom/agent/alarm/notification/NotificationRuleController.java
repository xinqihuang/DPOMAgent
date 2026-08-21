package com.dpom.agent.alarm.notification;

import com.dpom.agent.alarm.domain.NotificationRule;
import com.dpom.agent.alarm.persistence.NotificationRuleDao;
import com.dpom.agent.alarm.persistence.command.NotificationRuleInsert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 通知规则管理控制器：列表、新增、启停。
 *
 * <p>规则变更经 {@link NotificationRuleAdminService} 写审计。MVP 无鉴权，操作人固定为 {@code admin}。</p>
 */
@RestController
public class NotificationRuleController {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationRuleController.class);
    private static final String OPERATOR = "admin";

    private final NotificationRuleDao ruleDao;
    private final NotificationRuleAdminService ruleAdminService;

    /**
     * 构造规则管理控制器。
     *
     * @param ruleDao          规则持久化
     * @param ruleAdminService 规则管理服务
     */
    public NotificationRuleController(NotificationRuleDao ruleDao, NotificationRuleAdminService ruleAdminService) {
        this.ruleDao = ruleDao;
        this.ruleAdminService = ruleAdminService;
    }

    /**
     * 查询全部通知规则（含停用）。
     *
     * @return 规则列表
     */
    @GetMapping("/api/v1/notification/rules")
    public List<NotificationRule> list() {
        return ruleDao.findAll();
    }

    /**
     * 新增通知规则。
     *
     * @param request 新增请求
     * @return 新规则 id
     */
    @PostMapping("/api/v1/notification/rules")
    public ResponseEntity<Map<String, Long>> add(@RequestBody AddRuleRequest request) {
        NotificationRuleInsert command = new NotificationRuleInsert(request.name(), request.sourceFilter(),
                request.serviceCodeFilter(), request.resourceFilter(), request.severityFilter(), null,
                request.channels(), request.enabled());
        long id = ruleAdminService.addRule(command, OPERATOR);
        LOG.info("新增通知规则 id={} name={}", id, request.name());
        return ResponseEntity.ok(Map.of("id", id));
    }

    /**
     * 启停通知规则。
     *
     * @param id      规则 id
     * @param request 启停请求
     * @return 空 200
     */
    @PutMapping("/api/v1/notification/rules/{id}/enabled")
    public ResponseEntity<Void> setEnabled(@PathVariable long id, @RequestBody SetEnabledRequest request) {
        ruleAdminService.setEnabled(id, request.enabled(), OPERATOR);
        LOG.info("通知规则启停 id={} enabled={}", id, request.enabled());
        return ResponseEntity.ok().build();
    }
}
