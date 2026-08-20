package com.dpom.agent.alarm.ingestion;

import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.springframework.stereotype.Component;

/**
 * APM 告警标准化器。
 */
@Component
public class ApmAlarmNormalizer extends AbstractAlarmNormalizer {

    @Override
    public AlarmSource source() {
        return AlarmSource.APM;
    }

    @Override
    protected SeverityLevel mapSeverity(String raw) {
        if (raw == null) {
            return SeverityLevel.WARNING;
        }
        return switch (raw.toUpperCase()) {
            case "FATAL", "ERROR" -> SeverityLevel.CRITICAL;
            case "WARN", "WARNING" -> SeverityLevel.WARNING;
            case "INFO" -> SeverityLevel.INFO;
            default -> SeverityLevel.WARNING;
        };
    }
}
