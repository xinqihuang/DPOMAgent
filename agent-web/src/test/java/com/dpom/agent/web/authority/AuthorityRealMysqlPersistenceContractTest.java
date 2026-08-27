package com.dpom.agent.web.authority;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** 显式指向独立测试 schema 后才执行的真实 MySQL 8 权威持久化契约。 */
@EnabledIfEnvironmentVariable(named = "DPOM_AUTHORITY_REAL_MYSQL_URL", matches = ".+",
        disabledReason = "未设置独立的 DPOM_AUTHORITY_REAL_MYSQL_URL")
@SpringJUnitConfig(AuthorityPersistenceTestConfiguration.class)
@TestPropertySource(properties = {
        "mybatis.mapper-locations=classpath*:com/dpom/agent/core/persistence/mapper/*.xml",
        "management.endpoint.health.validate-group-membership=false"
})
class AuthorityRealMysqlPersistenceContractTest extends AbstractAuthorityPersistenceContract {

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AuthorityRealMysqlPersistenceContractTest::validatedUrl);
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("DPOM_AUTHORITY_REAL_MYSQL_USER", "root"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("DPOM_AUTHORITY_REAL_MYSQL_PASSWORD", ""));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Override
    void beforeSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS authority_publication_attempt");
        jdbcTemplate.execute("DROP TABLE IF EXISTS authority_publication_intent");
        jdbcTemplate.execute("DROP TABLE IF EXISTS authority_diagnosis_source");
        jdbcTemplate.execute("DROP TABLE IF EXISTS authority_audit");
        jdbcTemplate.execute("DROP TABLE IF EXISTS authority_tool_use");
        jdbcTemplate.execute("DROP TABLE IF EXISTS authority_investigation_revision");
        jdbcTemplate.execute("DROP TABLE IF EXISTS authority_investigation_head");
    }

    @Override
    Resource schemaResource() {
        return new ClassPathResource(
                "db/deployment/authority-realignment/001_authority_forward.sql");
    }

    private static String validatedUrl() {
        String url = System.getenv("DPOM_AUTHORITY_REAL_MYSQL_URL");
        if (url == null || !url.matches(
                "(?i)^jdbc:mysql://[^/]+/[^?]*(?:_test|_contract)(?:\\?.*)?$")) {
            throw new IllegalStateException("AUTHORITY_MYSQL_DEDICATED_SCHEMA_REQUIRED");
        }
        return url;
    }
}
