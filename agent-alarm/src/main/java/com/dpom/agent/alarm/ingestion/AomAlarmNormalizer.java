package com.dpom.agent.alarm.ingestion;

import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.springframework.stereotype.Component;

/**
 * AOM 告警标准化器。
 */
@Component
public class AomAlarmNormalizer extends AbstractAlarmNormalizer {

    @Override
    public AlarmSource source() {
        return AlarmSource.AOM;
    }

    @Override
    protected SeverityLevel mapSeverity(String raw) {
        if (raw == null) {
            return SeverityLevel.WARNING;
        }
        return switch (raw.toUpperCase()) {
            case "CRITICAL", "MAJOR" -> SeverityLevel.CRITICAL;
            case "MINOR" -> SeverityLevel.WARNING;
            case "INFO", "INFORMATIONAL" -> SeverityLevel.INFO;
            default -> SeverityLevel.WARNING;
        };
    }
}
