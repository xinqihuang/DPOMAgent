# Tasks

## 1. OpenSpec 与依赖
- [x] 1.1 建立 Change 与 delta spec，openspec validate --strict 通过
- [x] 1.2 父 POM property/dependencyManagement 管理 mybatis 版本；删除重复 maven-compiler-plugin 配置

## 2. XML Mapper 与类型化参数
- [x] 2.1 为 12 个持久化 Mapper + HealthCheck 各写一个 XML（显式列清单、resultMap/constructor、useGeneratedKeys）
- [x] 2.2 引入类型化 INSERT command 类（含可回填 Long id），移除 Map/insertRaw/appendRaw
- [x] 2.3 Java Mapper 接口仅保留类型安全方法签名，无 SQL 注解/字符串
- [x] 2.4 多参数方法显式 @Param；配置 mapper-locations

## 3. 调用方与测试
- [x] 3.1 更新 services/tests 调用方使用类型化 command 构造插入并回读 id
- [x] 3.2 H2 全量测试 mvn clean verify 通过
- [x] 3.3 真实 MySQL 8.0 契约（Testcontainers 或受控外部实例）覆盖全部 13 个 Mapper 与核心语义（insert/append 回填主键+select round-trip、nullable/enum/boolean/LocalDateTime、update 影响行数/条件更新、幂等、唯一约束、追加式审计），真实执行并记录 REAL_EXECUTED 路径
- [x] 3.4 Flyway 迁移不可变：恢复 V8 到 HEAD；实现自动 clean-install baseline（MySqlFreshBaselineMigrationStrategy + 版本受控 baseline SQL，仅空 MySQL 自动执行），分别验证「全新 RDS MySQL 安装」与「已执行旧 V8 的非 MySQL 环境升级」
- [x] 3.5 统一验收口径：spec/design/proposal/report 写「真实 MySQL 8.0 契约（Testcontainers 或受控外部实例）」并明确 REAL_EXECUTED 路径
- [x] 3.6 自动 baseline 测试：空 MySQL 一次启动成功、二次启动幂等、已有历史校验升级、部分 schema 拒绝、H2 原路径不受影响；真实 MySQL 验证前三个适用场景

## 4. 清理与验收
- [x] 4.1 扫描 main 源码：无 JDBC API、无 SQL 注解/字符串、无 Map insertRaw、无 SELECT *
- [x] 4.2 同步 README/config.yaml/AGENTS 说明为 MyBatis XML Mapper + RDS for MySQL
- [x] 4.3 写 docs/replace-jdbc-with-mybatis-acceptance-report.md