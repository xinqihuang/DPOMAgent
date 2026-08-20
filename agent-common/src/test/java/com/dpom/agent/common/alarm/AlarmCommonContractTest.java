package com.dpom.agent.common.alarm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 告警公共契约单测：覆盖枚举取值与触发端口 DTO 工厂方法。
 */
class AlarmCommonContractTest {

    @Test
    void alarmSourceCoversHuaweiCloudServices() {
        assertThat(AlarmSource.values()).containsExactlyInAnyOrder(
                AlarmSource.AOM, AlarmSource.CES, AlarmSource.APM, AlarmSource.LTS);
    }

    @Test
    void severityLevelOrderedCriticalWarningInfo() {
        assertThat(SeverityLevel.values()).containsExactly(
                SeverityLevel.CRITICAL, SeverityLevel.WARNING, SeverityLevel.INFO);
    }

    @Test
    void alarmIncidentStatusCoversLifecycle() {
        assertThat(AlarmIncidentStatus.values()).containsExactly(
                AlarmIncidentStatus.OPEN, AlarmIncidentStatus.ACKNOWLEDGED, AlarmIncidentStatus.RESOLVED);
    }

    @Test
    void triggeredResultCarriesInvestigationId() {
        AlarmIncidentTriggerResult result = AlarmIncidentTriggerResult.triggered(42L);
        assertThat(result.triggered()).isTrue();
        assertThat(result.investigationId()).isEqualTo(42L);
        assertThat(result.skipReason()).isNull();
    }

    @Test
    void skippedResultCarriesReason() {
        AlarmIncidentTriggerResult result = AlarmIncidentTriggerResult.skipped("端口未装配");
        assertThat(result.triggered()).isFalse();
        assertThat(result.investigationId()).isNull();
        assertThat(result.skipReason()).isEqualTo("端口未装配");
    }
}
