# 本地环境凭据（模板，请勿填写真实密码提交）

本文件是 docs/local-environment.md 的脱敏模板。真实凭据只保存在本地（该文件已加入 .gitignore）。

## MySQL（3306）
- 主机：localhost
- 端口：3306
- 用户：root（或 dpom）
- 密码：通过环境变量 DPOM_DB_PASSWORD 注入（不要写死）
- 数据库：dpom_agent（Flyway 管理迁移）

## Redis（6379）
- 主机：localhost
- 端口：6379
- 密码：无（默认本地实例无需 AUTH）

## 外部能力（MCP / LLM）
- LLM：DeepSeek（OpenAI 兼容 /chat/completions），模型 deepseek-v4-pro。
- API Key：通过环境变量 DEEPSEEK_API_KEY 注入。
- CodeGraphContext：MCP-over-SSE，\`dpom.codegraph.mcp-base-url\`（默认 http://localhost:8080）。
- Drain3：MCP Server（drain3-mcp-server），运行 \`drain3-mcp-server --transport sse --port 8100\`；\`dpom.logtemplate.mcp-base-url\` 默认 http://localhost:8100。

## 本地运行测试
\`\`\`
$env:DPOM_DB_PASSWORD='<本机 MySQL 密码>'
$env:DEEPSEEK_API_KEY='<DeepSeek API Key>'
mvn clean verify
\`\`\`
