# Replace JDBC with MyBatis

## Why

DPOMAgent 持久化层当前用 Spring `JdbcClient` 手写 SQL 与 `RowMapper`，SQL 散落在 Java 字符串中，
字段映射依赖列顺序，AUTO_INCREMENT 用 `GeneratedKeyHolder` + `GeneratedKeys` 提取，可读性与可维护性差。
生产目标库为华为云 RDS for MySQL（8.0），需要显式、可审计、类型安全的持久化实现。

## What changes

- 全部 SQL 从 Java 移出，改为 MyBatis XML Mapper（每个 Mapper 一个 XML，namespace=接口全限定名）。
- Java Mapper 只保留类型安全方法签名，禁止注解 SQL、字符串 SQL、`Map<String,Object>` 与 `insertRaw/appendRaw` 弱类型胶水。
- AUTO_INCREMENT 插入使用类型化 mutable command 参数（含可回填 `Long id`），领域 record 与 API 契约保持不变。
- 所有 record 查询使用显式 `resultMap`/`constructor` 映射，不依赖数据库列顺序，禁止 `SELECT *`。
- 明确生产目标为 RDS for MySQL 8.0；H2 仅用于快速测试，不作为最终兼容性证明。
- 增加真实 MySQL 8.0 Mapper 契约测试（Testcontainers 或受控外部实例）；按真实执行路径记录 `REAL_EXECUTED`，Docker 与外部实例均不可用时才标记 `REAL_MYSQL_NOT_EXECUTED`，不冒充通过。
- 新增真实 MySQL 空库自动 clean-install baseline（自定义 `FlywayMigrationStrategy` + 受版本控制 baseline SQL，仅空 MySQL 自动执行），不改已发布 migration，fail-closed。

## Boundaries

- 只替换持久化实现，不改变领域 record/API、事务、幂等、并发、审计语义。
- 不引入 RAG/Embedding、任意 shell、自动生产执行；保持单实例 Java Web。
- 保留原 SQL 的排序、LIMIT、条件更新、CURRENT_TIMESTAMP、唯一键幂等与追加式审计。
