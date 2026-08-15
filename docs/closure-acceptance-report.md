# DPOMAgent 仓库收口验收报告

日期：2026-08-14
Change：bootstrap-investigation-agent（已归档）

## 一、完成项（9 项全部完成）

1. ✅ **中文乱码修复**：对 AGENTS.md、openspec、docs、Java 源码全量扫描（ripgrep 匹配 U+FFFD 替换符 = 0），所有 .md/.java/.yml/.xml 均为合法 UTF-8，中文正常渲染，无乱码，未改动业务逻辑。
2. ✅ **审计 T001–T014 + tasks.md checkbox**：逐项核对实现与测试，tasks.md 已改为可追踪 checkbox，每条附验证测试；判定基于测试行为而非文件存在。其中 T013 记为“最小基准骨架完成”、T014 记为“Drain3 MCP 接口与映射测试完成”（未完成事实见下文“未完成项”）。
3. ✅ **current truth spec**：创建 \`openspec/specs/investigation-agent/spec.md\`，与实际代码一致（CGC/Drain3 走 MCP、13 工具、模块依赖方向、默认测试隔离、Redis 仅缓存）。
4. ✅ **mine_log_templates schema**：\`lines\` 从 string 改为 string array；新增 \`InvestigationToolExecutorTest.mineLogTemplatesLinesIsStringArray\` 锁定。
5. ✅ **模块依赖方向**：项目内部模块依赖中，agent-core 只依赖 agent-common、不依赖任何 adapter；agent-web 作为 composition root 显式依赖三者；Port/DTO 契约均在 agent-common。（agent-core 仍使用 Spring JDBC、Jackson、Redis 等第三方库，属正常，不在该限制内。）
6. ✅ **默认测试隔离**：测试数据源改为 H2（MySQL 模式，内存）；SnapshotCacheTest 改为 Mock StringRedisTemplate；DeepSeek/Drain3/CGC 仅存在于 E2E（\`DPOM_E2E=true\` 显式启用）。
7. ✅ **构建**：\`mvn clean verify\` 与 \`mvn -pl agent-web -am package\` 均 BUILD SUCCESS。
8. ✅ **验收报告**：本文件。
9. ✅ **归档 + 同步 current truth**：Change 已归档，主 spec 已就位。

## 二、未完成项（下一阶段，非 blocker）

| 项 | 说明 |
|---|---|
| T013 真实 E2E Regression & Benchmark | 本轮仅完成最小基准骨架（数据集定义 + 报告占位）。独立 \`evals/\`、独立 fixtures、机器可读指标计算、真实外部链路（真实 LLM/CGC/Drain3）仍属下一阶段。 |
| T014 真实 Drain3 聚类/参数抽取 E2E | 本轮仅完成 Drain3 MCP 接口与映射测试；真实 Drain3 聚类与参数抽取端到端尚未在默认构建中验证（需真实 Drain3 sidecar）。 |

> 上述为范围收口后如实保留的未完成事实，不阻断当前 Change 归档（归档保持，不撤销）。

## 三、跳过的测试及原因

| 测试 | 原因 |
|---|---|
| CodeWorkspaceTest（1 个 symlink 用例） | Windows 非管理员无法创建符号链接，测试内声明跳过 |
| Log4jStacktraceE2ETest（1 个） | 需真实 DeepSeek + CodeGraphContext + Drain3，由 \`DPOM_E2E=true\` 显式启用，默认跳过 |

## 四、架构边界检查结果

- agent-core 依赖：仅 agent-common + spring-jdbc + jackson + data-redis + test（pom 无 adapter，main 源码 0 处 import \`com.dpom.agent.adapter\`）。
- agent-web 依赖：agent-core + agent-adapter-llm/runtime/codegraph（composition root），\`scanBasePackages=com.dpom.agent\` 装配 bean。
- Port/DTO 契约：ModelClient / CodeGraphClient / RuntimeEvidenceClient / LogTemplateMinerClient 及全部 DTO 均在 agent-common。
- Toolset：13 个工具，无 execute_shell。
- 边界合规：No RAG/Embedding/Vector DB；无任意 shell 工具；Mitigation 仅生成 Artifact 且 REQUIRES_APPROVAL；单实例 Java Web。

## 五、实际测试数量

**55 个测试（53 通过，2 跳过，0 失败）**

| 模块 | 测试数 | 跳过 |
|---|---|---|
| agent-adapter-llm | 8 | 0 |
| agent-adapter-runtime | 6 | 0 |
| agent-adapter-codegraph | 5 | 0 |
| agent-core | 16 | 1 |
| agent-web | 20 | 1 |
| **合计** | **55** | **2** |

## 六、归档结果

- Change 已移动：\`openspec/changes/archive/2026-08-14-bootstrap-investigation-agent/\`
- current truth：\`openspec/specs/investigation-agent/spec.md\`
- 当前 active changes：无。
