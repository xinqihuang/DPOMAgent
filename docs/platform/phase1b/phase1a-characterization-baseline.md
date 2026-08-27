# Phase 1A 迁移前特征基线

记录时间：2026-08-25（Asia/Shanghai）  
适用变更：`complete-phase1-three-service-convergence`  
目的：在 Phase 1B 改造前固定现有兼容链路、共享契约和真实数据库门禁的可重复证据。

## 固定工具链

- Java：Oracle JDK 21.0.11（`C:\Program Files\Java\jdk-21.0.11`）
- Maven：3.9.9（`D:\tools\apache-maven-3.9.9\bin\mvn.cmd`）
- Python：DeepEval 使用 Python 3.12；依赖由 `python -m uv` 按 lockfile 解析
- OpenSpec：1.9.0

凭据只在测试子进程中临时注入，未写入本报告、命令日志或仓库文件。

## 结果

| 范围 | 命令/门禁 | 结果 |
|---|---|---|
| DPOMAgent 全量兼容基线 | `mvn clean verify` | BUILD SUCCESS；457 tests，0 failures，0 errors，28 skipped |
| DPOMAgent 共享 Diagnosis Event v1、canonicalization、outbox 与 HTTP delivery | 包含在全量构建及 fixture/acceptance 测试中 | PASS；v1 资产保持兼容 |
| DPOMAgent 真实 MySQL 8 | 仅运行 `MybatisExternalMysqlContractTest`，指向既有本地 `dpom_agent` | REAL_EXECUTED；15 tests，0 failures/errors/skips；Flyway schema v12；outbox 契约通过 |
| SRE Intelligence 全量摄取、规则判定、语义判定与 suite | `mvn clean verify` | BUILD SUCCESS；127 tests，0 failures，0 errors，4 skipped |
| SRE 共享契约 fixture 与 HTTP 摄取 | 包含在全量构建中 | PASS |
| SRE 真实 MySQL 8 | `mvn -Pmysql-contract verify`，使用隔离库 `sre_intelligence_phase1b_contract` | BUILD SUCCESS；`MYSQL_CONTRACT_STATUS=EXECUTED`；1 IT，0 failures/errors/skips；schema v4 |
| DeepEval 静态与单元基线 | `ruff check .`、`mypy src`、`pytest -q`（Python 3.12、`--all-extras --frozen`） | Ruff PASS；mypy 14 source files PASS；22 tests PASS |

## MySQL 隔离与变更范围

- DPOMAgent 的既有测试自行清理其生成的契约测试记录；未创建或删除业务数据库。
- SRE 测试会清空它拥有的评测表，因此先创建专用库 `sre_intelligence_phase1b_contract`，未指向 `dpom_agent` 或其他业务库。
- SRE 门禁仅在 `SRE_MYSQL_TEST_ALLOW_MUTATION=true` 的测试子进程内启用。
- 两组真实 MySQL 测试均确认连接 MySQL 8.0，且最终 Maven reactor 为 `BUILD SUCCESS`。

## 基线解释

该结果只证明 Phase 1A 兼容竖切面在迁移前可工作，不证明 Phase 1B 三服务目标已完成。Phase 1B 后续必须保持这些兼容资产可验证，同时把在线调查权威迁移到 DPOMBase、把 Kafka/HTTP 统一摄取落到 SRE，并保持 DeepEval 无状态。

工作树与所有权基线见 `baseline-inventory.md`，逐路径状态见 `baseline-worktree-status.txt`。
