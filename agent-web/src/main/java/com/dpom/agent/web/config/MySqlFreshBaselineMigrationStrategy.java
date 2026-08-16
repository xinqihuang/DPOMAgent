package com.dpom.agent.web.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ScriptException;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * MySQL 全新空库的自动 clean-install baseline 策略（fail-closed）。
 *
 * <p>历史迁移不可变：已发布的 {@code V8__evidence_handoff.sql} 用 MySQL 8.0 保留字
 * {@code release}/{@code commit} 作裸列名，在真实 MySQL 8.0 上无法执行（历史基线缺陷）。
 * 本策略仅在「明确识别为 MySQL 且目标 schema 为空且无 flyway_schema_history」时，
 * 自动执行受版本控制的 baseline SQL，再由 Flyway 建立 version 9 基线；
 * 非 MySQL（H2）、非空库、已有历史、升级路径一律不介入。</p>
 */
public class MySqlFreshBaselineMigrationStrategy implements FlywayMigrationStrategy {

    private static final String EXPECTED_MAJOR_VERSION = "8";
    private static final String HISTORY_TABLE = "flyway_schema_history";

    private final DataSource dataSource;
    private final Resource baselineSql;
    private final boolean enabled;

    public MySqlFreshBaselineMigrationStrategy(DataSource dataSource, Resource baselineSql, boolean enabled) {
        this.dataSource = dataSource;
        this.baselineSql = baselineSql;
        this.enabled = enabled;
    }

    @Override
    public void migrate(Flyway flyway) {
        boolean baselineApplied = false;
        if (enabled) {
            baselineApplied = prepareFreshMySqlBaselineIfNeeded();
        }
        if (baselineApplied) {
            flyway.baseline();
        }
        flyway.migrate();
    }

    /**
     * 仅当「MySQL 8.x + 空 schema + 无历史」时执行 baseline SQL，返回是否已执行。
     *
     * @return true 表示已执行 baseline SQL（随后由调用方建立 Flyway baseline）
     */
    private boolean prepareFreshMySqlBaselineIfNeeded() {
        try (Connection conn = dataSource.getConnection()) {
            String product = conn.getMetaData().getDatabaseProductName();
            if (product == null || !product.toLowerCase().contains("mysql")) {
                return false;
            }
            String version = querySingle(conn, "SELECT VERSION()");
            String major = majorVersion(version);
            if (!EXPECTED_MAJOR_VERSION.equals(major)) {
                throw new IllegalStateException("MySQL 主版本不匹配（期望 8.x，实际 " + version
                        + "），拒绝自动初始化基线（fail-closed）。请使用 MySQL 8.0+ 或人工处理 schema。");
            }
            if (tableExists(conn, HISTORY_TABLE)) {
                return false;
            }
            int tableCount = countTables(conn);
            if (tableCount == 0) {
                applyBaseline(conn);
                return true;
            }
            throw new IllegalStateException("目标 MySQL schema 非空且无 Flyway 历史（存在 " + tableCount
                    + " 张表），拒绝自动初始化以避免覆盖已有数据（fail-closed）。请人工核查或清理后重试。");
        } catch (SQLException e) {
            throw new IllegalStateException("检测 MySQL schema 初始化状态失败，拒绝启动（fail-closed）。", e);
        }
    }

    private void applyBaseline(Connection conn) throws SQLException {
        try {
            ScriptUtils.executeSqlScript(conn, baselineSql);
        } catch (ScriptException e) {
            throw new SQLException("执行 clean-install baseline SQL 失败：" + e.getMessage(), e);
        }
    }

    private String querySingle(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private int countTables(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String majorVersion(String version) {
        if (version == null || version.isEmpty()) {
            return "";
        }
        int dot = version.indexOf('.');
        return dot < 0 ? version : version.substring(0, dot);
    }
}
