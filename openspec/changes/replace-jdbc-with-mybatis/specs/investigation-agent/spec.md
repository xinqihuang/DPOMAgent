## MODIFIED Requirements

### Requirement: Module Dependency Direction
在项目内部模块依赖中，agent-core 只依赖 agent-common、不依赖任何 adapter；Port/DTO 契约 SHALL 放在 agent-common；
agent-web SHALL 作为 composition root 组装 Core 与 Adapter。本要求不限制 agent-core 使用 MyBatis、Jackson、Redis 等第三方库。
#### Scenario: Dependency Graph
WHEN 检查项目内部模块依赖
THEN agent-core SHALL 只依赖 agent-common，不依赖 adapter
AND agent-web SHALL 依赖 agent-core 与 agent-adapter-{llm,runtime,codegraph}
AND adapter SHALL 只依赖 agent-common。

## ADDED Requirements

### Requirement: MyBatis XML Mapper Persistence
持久化层 SHALL 使用 MyBatis XML Mapper：SQL 全部放 XML（一个 Mapper 一个 XML，namespace=接口全限定名），
Java Mapper 只保留类型安全方法签名，禁止注解 SQL、字符串 SQL、Map 弱类型胶水。AUTO_INCREMENT 插入 SHALL 使用类型化
mutable command 参数（含可回填 Long id）。record 查询 SHALL 使用显式 resultMap/constructor 映射，禁止 SELECT *。
生产目标库为 RDS for MySQL 8.0，SQL 兼容 MySQL 8.0；H2 仅用于快速测试，真实兼容性由真实 MySQL 8.0 契约（Testcontainers 或受控外部实例，见 design D7）证明。契约测试必须真实执行并记录 REAL_EXECUTED 路径，不得以 mock、静态扫描或跳过冒充通过。
#### Scenario: No SQL In Java
WHEN 检查 Mapper Java 接口
THEN SHALL NOT 出现 @Select/@Insert/@Update/@Delete/@*Provider 或字符串 SQL
AND 所有 SQL SHALL 位于 XML mapper。
#### Scenario: Typed Insert Command
WHEN 插入带自增主键的记录
THEN SHALL 使用类型化 mutable command 参数回填 Long id
AND SHALL NOT 使用 Map<String,Object> 或 insertRaw 弱类型胶水。
#### Scenario: Explicit Column Mapping
WHEN 查询 record
THEN SHALL 使用显式列清单与 resultMap/constructor 映射
AND SHALL NOT 使用 SELECT * 或依赖数据库列顺序。
#### Scenario: MySQL 8.0 Auto Clean-Install Baseline
WHEN 启动应用且目标库为全新空 MySQL 8.x（无 flyway_schema_history）
THEN 应用 SHALL 自动执行受版本控制的 baseline 并建立 Flyway version 9 基线
AND SHALL NOT 修改任何已发布 migration 的 checksum
AND 非空 schema、已有历史或非 MySQL 环境 SHALL NOT 被误初始化
AND 部分表存在、版本不匹配或 baseline 失败 SHALL fail-closed 并终止启动。
