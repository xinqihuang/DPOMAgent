package com.dpom.agent.alarm.governance;

import com.dpom.agent.alarm.domain.Alarm;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警分组器：按服务与资源维度分组。
 */
@Service
public class AlarmGrouper {

    /**
     * 按服务编码 + 资源标识分组。
     *
     * @param alarms 告警列表
     * @return 分组结果（键为 service|resource）
     */
    public Map<String, List<Alarm>> groupByServiceAndResource(List<Alarm> alarms) {
        Map<String, List<Alarm>> groups = new LinkedHashMap<>();
        for (Alarm alarm : alarms) {
            String key = groupKey(alarm);
            groups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(alarm);
        }
        return groups;
    }

    private static String groupKey(Alarm alarm) {
        String service = alarm.serviceCode() == null ? "" : alarm.serviceCode();
        String resource = alarm.resourceId() == null ? "" : alarm.resourceId();
        return service + "|" + resource;
    }
}
