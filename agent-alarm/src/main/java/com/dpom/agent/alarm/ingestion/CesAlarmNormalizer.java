package com.dpom.agent.alarm.ingestion;

import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.springframework.stereotype.Component;

/**
 * CES 告警标准化器。
 */
@Component
public class CesAlarmNormalizer extends AbstractAlarmNormalizer {

    @Override
    public AlarmSource source() {
        return AlarmSource.CES;
    }

    @Override
    protected SeverityLevel mapSeverity(String raw) {
        if (raw == null) {
            return SeverityLevel.WARNING;
        }
        return switch (raw.toUpperCase()) {
            case "URGENT", "CRITICAL" -> SeverityLevel.CRITICAL;
            case "WARNING", "IMPORTANT" -> SeverityLevel.WARNING;
            case "INFO" -> SeverityLevel.INFO;
            default -> SeverityLevel.WARNING;
        };
    }
}
