# replace-jdbc-with-mybatis 验收报告（可独立复验·自动 baseline 整改后）

日期：2026-08-16
状态：**可独立复验**（未归档、未提交、未推送）
真实 MySQL 契约：**REAL_EXECUTED**（本机 MySQL 8.0.46，自动 clean-install baseline，覆盖全部 13 个 Mapper）

## 0. 整改结论

| 项 | 结论 |
|---|---|
| P1-1 Flyway 历史迁移不可变 | ✅ V8 与 HEAD 完全一致（不改 checksum）；全新 MySQL 空库**自动** clean-install baseline，不要求手工执行 SQL |
| P1-2 真实 MySQL 覆盖不足 | ✅ 契约套件 14 测试覆盖全部 13 Mapper 与核心语义，真实执行 REAL_EXECUTED |
| P1-3 统一验收口径 | ✅ spec/design/proposal/report 统一并明确 REAL_EXECUTED 路径 |
| 全新 MySQL 默认部署路径 | ✅ 开箱可部署：空库一次启动自动建 schema + baseline |

## 1. P1-1 自动 clean-install baseline（Flyway 迁移不可变）

### 1.1 历史基线缺陷
- 已发布 `V8__evidence_handoff.sql`（commit 5dd63b2）的 `handoff_import` 用 MySQL 8.0 保留字 `release`/`commit` 作裸列名，真实 MySQL 8.0 上无法执行（已复现 `ERROR 1064 ... near 'release VARCHAR(128), commit VARCHAR(64)...'`）。
- **V8 与 HEAD 完全一致**（`git status` 无 diff，`ls-files --eol` 正常），不改变历史 checksum。

### 1.2 自动初始化方案（应用内受控，非手工）
- 新增 `MySqlFreshBaselineMigrationStrategy`（自定义 `FlywayMigrationStrategy`），在 Flyway 迁移前执行：仅在**「明确识别为 MySQL + 主版本 8.x + 目标 schema 为空（0 张表）+ 无 flyway_schema_history」**时，自动执行版本受控的 `db/baseline/mysql8_baseline.sql`（等价 V1–V9 全量 schema，仅 `release`/`commit` 加反引号），随后显式 `Flyway.baseline()` 建立 version 9 基线（`spring.flyway.baseline-version: 9`）。
- 非 MySQL（H2）、非空库、已有 history、升级路径**一律不介入**，走正常 Flyway。
- 配置：`dpom.flyway.mysql-baseline.enabled`（默认 true）+ `dpom.flyway.mysql-baseline.location`（默认 `classpath:db/baseline/mysql8_baseline.sql`），均可用环境变量覆盖。

### 1.3 fail-closed（禁止破坏已有 schema）
- 主版本不匹配（非 8.x）、部分表存在（非空且无 history）、baseline 执行失败/中断、权限不足、状态检测失败 → 抛出**无凭据**错误并终止启动；绝不删除或重建非空 schema。

### 1.4 验证（真实 MySQL 8.0.46，REAL_EXECUTED）
| 场景 | 结果 | 证据 |
|---|---|---|
| 空 MySQL 一次启动成功 | ✅ | 空库 `dpom_agent_verify` 上 `MybatisExternalMysqlContractTest` 启动时自动 `Successfully baselined schema with version: 9`，14 测试全通过 |
| 二次启动幂等 | ✅ | `MySqlFreshBaselineTest.secondStartupIsIdempotent`：两次 migrate 后 history 仍 1 条 baseline，无重复 |
| 已有 history 校验/升级 | ✅ | 二次启动时 Flyway validate 通过、无重复 baseline；未来 V10+ 由 baseline@9 后正常应用 |
| 部分 schema 拒绝 | ✅ | `MySqlFreshBaselineTest.partialSchemaFailsClosed`：预建 1 张表后启动抛 `IllegalStateException`（含 fail-closed） |
| H2 原路径不受影响 | ✅ | `mvn clean verify` H2 全量 BUILD SUCCESS（V1–V9 正常迁移，不触发 baseline） |

## 2. P1-2 真实 MySQL 契约覆盖（14 测试 / 全部 13 Mapper）

`MybatisExternalMysqlContractTest` 在空 MySQL 上启动即自动 baseline，随后 **Tests run: 14, Failures: 0, Errors: 0, Skipped: 0**，`MVN_EXIT=0`。覆盖：HealthCheckMapper、IncidentDao、InvestigationDao（enum/nullable Long/int/LocalDateTime/条件更新）、ToolCallAuditDao（Boolean/nullable Long/追加）、EvidenceBundleDao（JSON）、InvestigationRunDao（finish）、InvestigationApiRequestDao（幂等/生命周期）、HypothesisDao（enum/updateStatus）、ConclusionDao、ScriptArtifactDao（script_type→type/boolean）、InvestigationStepDao（stepOrder/maxStepOrder）、ObservationDao、EvidenceHandoffDao（escalation/upload/approve/reject/uploaded/import 唯一约束+并发/append-only audit）。唯一键用 UUID，`@AfterAll` 清空 15 张表。

## 3. P1-3 统一验收口径
- spec（新增 Scenario「MySQL 8.0 Auto Clean-Install Baseline」）、design（D5/D7）、proposal、本报告统一为**「真实 MySQL 8.0 契约（Testcontainers 或受控外部实例）」**并明确 REAL_EXECUTED 路径与自动 baseline 语义；tasks 3.4/3.6 在真实执行通过后才勾选。

## 4. 构建与测试

`mvn clean verify`（JDK 21.0.11 + Maven 3.9.16）→ **BUILD SUCCESS**（H2 全量）：

| 模块 | 测试 | Failures | Errors | Skipped |
|---|---|---|---|---|
| agent-adapter-llm | 8 | 0 | 0 | 0 |
| agent-adapter-runtime | 6 | 0 | 0 | 0 |
| agent-adapter-codegraph | 39 | 0 | 0 | 0 |
| agent-core | 101 | 0 | 0 | 1 |
| agent-web | 156 | 0 | 0 | 25 |
| **合计** | **310** | **0** | **0** | **26** |

skip 26 = E2E 6 + `MybatisMapperContractTest`(Testcontainers) 2（Docker 不可用）+ `MybatisExternalMysqlContractTest` 14 + `MySqlFreshBaselineTest` 3（后两者在无 `DPOM_REAL_MYSQL_URL` 时按设计跳过）。

## 5. Mapper ↔ XML 对应与残留扫描（不变，仍通过）
- 13 Mapper ↔ 13 XML：namespace/statement id/显式列/`useGeneratedKeys`/`javaType` 全对应；`EvidenceHandoffDao.xml` 对 `release`/`commit` 反引号（属本 Change 新增 XML，非已发布 migration）。
- main 源码扫描：SQL 注解 0、JDBC API 0、`SELECT *` 0、持久化包 `Map`/`insertRaw`/`appendRaw` 0、可执行 SQL 字符串 0。

## 6. 已知风险 / 残余风险
- 自动 baseline 需应用 DB 账号具备 DDL 权限（首启建表）；权限不足会 fail-closed 而非静默失败。
- baseline SQL 约 230 行 DDL，MySQL DDL 隐式提交、不可原子回滚；若首启中途被杀，遗留部分表 → 下次启动 fail-closed，需人工清理（绝不自动删除非空 schema）。
- 仅支持 MySQL 8.x 主版本（8.0/8.4）；5.7/其他版本 fail-closed。
- 单实例假设：并发多实例首启可能同时执行 baseline（项目为单实例 Java Web，不在范围内）。

## 7. 本次整改产生的文件变动
- 恢复：`V8__evidence_handoff.sql`（与 HEAD 一致，无 diff）。
- 新增：`config/MySqlFreshBaselineMigrationStrategy.java`、`config/FlywayBaselineConfiguration.java`、`db/baseline/mysql8_baseline.sql`、`MySqlFreshBaselineTest.java`。
- 扩充：`MybatisExternalMysqlContractTest.java`（14 测试）。
- 修改：`application.yml`（main/test：baseline-version + dpom.flyway.mysql-baseline）、`EvidenceHandoffDao.xml`、`design.md`、`specs/investigation-agent/spec.md`、`proposal.md`、`tasks.md`、本报告。
