package com.dpom.agent.alarm.correlation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 告警事件管理控制器：供管理面触发关联诊断全链路。
 *
 * <p>「采集并诊断」= 关联事件化 → 通知分发 → 处置工件（REQUIRES_APPROVAL）。处置工件不执行生产操作。</p>
 */
@RestController
public class AlarmIncidentAdminController {

    private static final Logger LOG = LoggerFactory.getLogger(AlarmIncidentAdminController.class);

    private final AlarmDiagnosisOrchestrator orchestrator;

    /**
     * 构造事件管理控制器。
     *
     * @param orchestrator 诊断编排
     */
    public AlarmIncidentAdminController(AlarmDiagnosisOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * 对指定告警执行关联诊断全链路，返回新建事件 id 列表。
     *
     * @param request 关联请求（告警 id 列表）
     * @return 新建事件 id 列表
     */
    @PostMapping("/api/v1/alarm-incidents/correlate")
    public ResponseEntity<Map<String, List<Long>>> correlate(@RequestBody CorrelateRequest request) {
        List<Long> alarmIds = request.alarmIds();
        if (alarmIds == null || alarmIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of());
        }
        List<Long> incidentIds = orchestrator.diagnose(alarmIds);
        LOG.info("关联诊断完成 alarmIds={} incidentIds={}", alarmIds, incidentIds);
        return ResponseEntity.ok(Map.of("incidentIds", incidentIds));
    }
}
