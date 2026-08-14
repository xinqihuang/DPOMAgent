# 持久化切换到本机 MySQL

当前集成测试用 H2（MySQL 模式）跑同一套 Flyway 迁移；生产配置已指向真实 MySQL。
要把测试也切到真实 MySQL（本机 3306）：

1. 管理员执行一次初始化（创建 `dpom_agent` 库与 `dpom` 用户）：
   `mysql -uroot -p < docs/sql/init-dpom.sql`
2. 把 `agent-web/src/test/resources/application.yml` 的 H2 数据源替换为：
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/dpom_agent?useSSL=false&serverTimezone=UTC&characterEncoding=utf8
       username: dpom
       password: dpom
       driver-class-name: com.mysql.cj.jdbc.Driver
   ```
3. `mvn clean verify`（Flyway 会对空库执行 V1/V2/V3 迁移）。
