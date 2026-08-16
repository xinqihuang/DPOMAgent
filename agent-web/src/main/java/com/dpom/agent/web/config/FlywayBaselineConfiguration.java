package com.dpom.agent.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import javax.sql.DataSource;

/**
 * Flyway 迁移策略装配：在 Flyway 迁移前，对全新 MySQL 空库自动执行 clean-install baseline。
 */
@Configuration
public class FlywayBaselineConfiguration {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(
            DataSource dataSource,
            @Value("${dpom.flyway.mysql-baseline.location:classpath:db/baseline/mysql8_baseline.sql}") Resource baselineSql,
            @Value("${dpom.flyway.mysql-baseline.enabled:true}") boolean enabled) {
        return new MySqlFreshBaselineMigrationStrategy(dataSource, baselineSql, enabled);
    }
}
