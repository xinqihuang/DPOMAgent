package com.dpom.agent.web;

import com.dpom.agent.web.config.MySqlFreshBaselineMigrationStrategy;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 全新 MySQL 空库自动 clean-install baseline 的集成测试（真实 MySQL，无 Docker）。
 *
 * <p>直接驱动 {@link MySqlFreshBaselineMigrationStrategy} + 真实 Flyway，覆盖：
 * 空库一次启动自动 baseline、二次启动幂等、部分 schema fail-closed。</p>
 */
@EnabledIfEnvironmentVariable(named = "DPOM_REAL_MYSQL_URL", matches = ".+",
        disabledReason = "未设置 DPOM_REAL_MYSQL_URL，跳过真实 MySQL baseline 测试")
class MySqlFreshBaselineTest {

    static final String URL = System.getenv("DPOM_REAL_MYSQL_URL");
    static final String USER = System.getenv().getOrDefault("DPOM_REAL_MYSQL_USER", "root");
    static final String PASSWORD = System.getenv().getOrDefault("DPOM_REAL_MYSQL_PASSWORD", "");

    private static final List<String> TABLES = List.of(
            "incident", "investigation", "investigation_run", "investigation_step",
            "observation", "hypothesis", "conclusion", "script_artifact", "tool_call_audit",
            "evidence_bundle", "investigation_api_request", "escalation_decision",
            "handoff_upload", "handoff_import", "handoff_audit");

    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = newDataSource();
        resetSchema(dataSource);
    }

    @Test
    void freshEmptyMySqlAutoBaselinesOnStartup() throws Exception {
        strategy().migrate(newFlyway());
        assertTablesExist();
        assertBaselineVersion(9);
    }

    @Test
    void secondStartupIsIdempotent() throws Exception {
        strategy().migrate(newFlyway());
        strategy().migrate(newFlyway());
        assertTablesExist();
        assertBaselineVersion(9);
        assertHistoryRowCount(1);
    }

    @Test
    void partialSchemaFailsClosed() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE partial_probe (id BIGINT PRIMARY KEY)");
        }
        assertThatThrownBy(() -> strategy().migrate(newFlyway()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fail-closed");
    }

    private MySqlFreshBaselineMigrationStrategy strategy() {
        return new MySqlFreshBaselineMigrationStrategy(dataSource,
                new ClassPathResource("db/baseline/mysql8_baseline.sql"), true);
    }

    private Flyway newFlyway() {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineVersion("9")
                .load();
    }

    private static DataSource newDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(URL);
        ds.setUsername(USER);
        ds.setPassword(PASSWORD);
        return ds;
    }

    private static void resetSchema(DataSource ds) throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS flyway_schema_history");
            for (String t : TABLES) {
                st.execute("DROP TABLE IF EXISTS " + t);
            }
        }
    }

    private void assertTablesExist() throws Exception {
        for (String table : TABLES) {
            assertThat(tableExists(table)).as("表应存在: %s", table).isTrue();
        }
        assertThat(tableExists("flyway_schema_history")).isTrue();
    }

    private boolean tableExists(String tableName) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM information_schema.tables "
                     + "WHERE table_schema = DATABASE() AND table_name = '" + tableName + "'")) {
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    private void assertBaselineVersion(int expected) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM flyway_schema_history "
                     + "WHERE type = 'BASELINE' ORDER BY installed_rank DESC LIMIT 1")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(expected);
        }
    }

    private void assertHistoryRowCount(int expected) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM flyway_schema_history")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(expected);
        }
    }
}
