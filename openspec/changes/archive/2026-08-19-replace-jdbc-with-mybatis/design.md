# Design: Replace JDBC with MyBatis

## Context

DPOMAgent 持久化层由 `JdbcClient` + `RowMapper` 实现，现统一迁移到 MyBatis XML Mapper。
目标数据库为华为云 RDS for MySQL 8.0；本地用 H2 快速跑测试，真实兼容性以真实 MySQL 8.0 契约（Testcontainers 或受控外部实例）为准（见 D7）。

## Decisions

### D1 XML Mapper 与 mapper-locations
- SQL 全部放 XML：`agent-core/src/main/resources/com/dpom/agent/core/persistence/mapper/*.xml`。
- 一个 Mapper 接口对应一个 XML，`namespace` 与接口全限定名一致。
- `application.yml` 显式配置 `mybatis.mapper-locations`；Java 中无 `@Select/@Insert/@Update/@Delete/@*Provider` 与字符串 SQL。

### D2 类型化命令参数（AUTO_INCREMENT）
- 每个带自增主键的表引入一个 mutable command 类（如 `IncidentInsert`），字段与列一一对应，含可回填 `Long id` 与 setter。
- Mapper 方法签名 `int insert(IncidentInsert command)`；XML 用 `useGeneratedKeys="true" keyProperty="id"`。
- 禁止 `Map<String,Object>`、`insertRaw/appendRaw`；领域 record 与 REST API 契约不变，不为回填主键污染领域对象。

### D3 显式 resultMap / constructor 映射
- 所有查询用显式列清单，禁止 `SELECT *`。
- record 查询使用 `<resultMap><constructor>` 按构造参数顺序显式声明 `column`→参数，特别处理 enum（EnumTypeHandler）、
  Boolean、nullable Long、LocalDateTime、`script_type`→`type`；不依赖数据库列顺序。

### D4 多参数显式 @Param
- 多参数方法显式 `@Param` 或类型化参数对象，不依赖 `-parameters` 猜测参数名。

### D5 RDS for MySQL 8.0 兼容
- 生产 SQL 以 MySQL 8.0 兼容语法为准；保留字用反引号；不引入数据库特有且 RDS 不支持语法。
- H2 仅快速测试；真实兼容性由真实 MySQL 8.0 契约证明（见 D7）。
- 已发现并记录历史基线缺陷：已发布 V8__evidence_handoff.sql 的 handoff_import 表用 MySQL 8.0 保留字 `release`/`commit` 作裸列名，真实 MySQL 8.0 上无法执行（H2 MODE=MySQL 较宽松掩盖）；V8 保持不可变，由 D7 的 clean-install baseline 兜底。

### D6 版本管理
- MyBatis 版本提到父 POM `<properties>` + `<dependencyManagement>`；删除重复的 maven-compiler-plugin 配置。

### D7 真实 MySQL 契约与 clean-install baseline
- 真实 MySQL 8.0 契约通过两条受控路径验证：Testcontainers mysql:8.0（Docker 可用时）或受控外部 MySQL 8.0 实例（Docker 不可用时，经 `DPOM_REAL_MYSQL_URL/USER/PASSWORD` 环境变量指向，专用一次性库）。验收报告必须写明 REAL_EXECUTED 的具体路径。
- Flyway 历史迁移不可变：已发布 migration 一律不改（不改 checksum）。`V8__evidence_handoff.sql` 因保留字在 MySQL 8.0 无法执行，属历史基线缺陷，采用**自动** clean-install baseline：`MySqlFreshBaselineMigrationStrategy`（自定义 `FlywayMigrationStrategy`）仅在「明确识别为 MySQL 8.x + 目标 schema 为空 + 无 flyway_schema_history」时，自动执行版本受控的 `db/baseline/mysql8_baseline.sql`（等价 V1–V9 全量 schema，仅 `release`/`commit` 加反引号），随后显式 `Flyway.baseline()` 建立 version 9 基线；不要求运维手工执行 SQL，不依赖 README 兜底。
- fail-closed：MySQL 主版本不匹配、部分表存在、baseline 执行中断/失败、权限不足、状态检测失败均抛出无凭据错误并终止启动；绝不删除或重建非空 schema。非 MySQL（H2）、非空库、已有历史、升级路径一律不介入（原路径不受影响）。
- 已执行旧 V8 的非 MySQL 环境（H2 等）不受影响：V8 checksum 未变，新 XML 已用反引号引用 `release`/`commit`，升级安全。

## Risks
- XML 与接口脱节：由 Mapper 契约测试（H2 + 真实 MySQL 8.0 契约）兜底。
- record 构造映射错位：由显式 `<constructor>` 列映射兜底，不依赖列顺序。
- 历史迁移与 MySQL 8.0 兼容：V8 保留字缺陷由 clean-install baseline 兜底，未来新迁移仍须真实 MySQL 契约验证。
