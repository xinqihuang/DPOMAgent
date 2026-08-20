package com.dpom.agent.web;

import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.command.IncidentInsert;
import com.dpom.agent.core.persistence.command.InvestigationInsert;
import com.dpom.agent.alarm.domain.Alarm;
import com.dpom.agent.alarm.persistence.AlarmDao;
import com.dpom.agent.alarm.persistence.command.AlarmInsert;
import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.AlarmStatus;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 MySQL 8.0 Mapper 契约测试（Testcontainers）。
 *
 * <p>本机 Docker 不可用时，{@code @Testcontainers(disabledWithoutDocker = true)} 会显式跳过本类
 * （测试报告中显示 Skipped），不会冒充通过；验收报告按 REAL_MYSQL_NOT_EXECUTED 记录。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class MybatisMapperContractTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("dpom_agent")
            .withUsername("dpom")
            .withPassword("dpom");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private IncidentDao incidentDao;

    @Autowired
    private InvestigationDao investigationDao;

    @Autowired
    private AlarmDao alarmDao;

    @Test
    void incidentInsertAndSelectRoundTripOnRealMysql() {
        IncidentInsert command = new IncidentInsert("asset-service", "prod", "1.2.3", "abc123def456",
                "创建设备成功但数据库无记录");
        incidentDao.insert(command);
        long id = command.getId();
        assertThat(id).isPositive();

        Incident incident = incidentDao.findById(id).orElseThrow();
        assertThat(incident.serviceCode()).isEqualTo("asset-service");
        assertThat(incident.commitSha()).isEqualTo("abc123def456");
        assertThat(incident.createdAt()).isNotNull();
    }

    @Test
    void investigationEnumNullableAndBudgetRoundTripOnRealMysql() {
        IncidentInsert incidentCommand = new IncidentInsert("asset-service", "prod", "1.0.0", "abc123", "症状");
        incidentDao.insert(incidentCommand);
        InvestigationInsert investigationCommand = new InvestigationInsert(incidentCommand.getId(),
                InvestigationStatus.CREATED, null, 50, 100, 1800, 5);
        investigationDao.insert(investigationCommand);
        long investigationId = investigationCommand.getId();

        Investigation investigation = investigationDao.findById(investigationId).orElseThrow();
        assertThat(investigation.status()).isEqualTo(InvestigationStatus.CREATED);
        assertThat(investigation.currentRunId()).isNull();
        assertThat(investigation.maxSteps()).isEqualTo(50);
        assertThat(investigation.createdAt()).isNotNull();
        assertThat(investigation.updatedAt()).isNotNull();
    }

    @Test
    void alarmInsertAndSelectRoundTripOnRealMysql() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        AlarmInsert command = new AlarmInsert(AlarmSource.AOM, "webhook", "ext-9", "fp-9", "res-9",
                "磁盘满", SeverityLevel.CRITICAL, AlarmStatus.FIRING, 1, now, now,
                "asset-service", "prod", "{\"disk\":99}", null);
        alarmDao.insert(command);
        Alarm alarm = alarmDao.findById(command.getId()).orElseThrow();
        assertThat(alarm.source()).isEqualTo(AlarmSource.AOM);
        assertThat(alarm.severity()).isEqualTo(SeverityLevel.CRITICAL);
        assertThat(alarm.rawPayload()).isEqualTo("{\"disk\":99}");
    }
}
