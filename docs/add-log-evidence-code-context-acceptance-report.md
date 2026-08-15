# add-log-evidence-code-context 验收整改报告（第三轮：rootCauseId 反循环）

日期：2026-08-14
Change：add-log-evidence-code-context（未归档，等待独立验收）

## 一、修复内容

rootCauseId 之前由 ConclusionEvaluator 从 expected.json / EvidenceBundle 反向推导，导致 LLM 即使判错根因也可能通过。已修复为：rootCauseId 只来自实际 Conclusion。

### 修改文件
- `agent-core/.../conclusion/Conclusion.java`：增加稳定 `rootCauseId` 字段（与自然语言 rootCause 分离）。
- `agent-core/.../persistence/ConclusionDao.java`：映射 `root_cause_id` 列。
- `agent-web/.../db/migration/V5__conclusion_root_cause_id.sql`：新增 Flyway 迁移（未改已发布迁移）。
- `agent-core/.../investigation/InvestigationDecision.java`：Conclude 增加 rootCauseId。
- `agent-core/.../investigation/SymptomBrain.java`：解析 rootCauseId、提示词 instruct 输出 rootCauseId=异常实际抛出点、证据束渲染标记【异常抛出点】。
- `agent-core/.../investigation/InvestigationCoordinator.java`：finalize 透传 rootCauseId，降级时置空。
- `agent-core/.../eval/ConclusionEvaluator.java`：rootCauseId 只读 `conclusion.rootCauseId()`，增加 ROOT_CAUSE_MISMATCH / ROOT_CAUSE_ID_NOT_VERIFIED 校验。
- `agent-core/.../eval/ConclusionEvaluatorTest.java`：新增负向测试。
- `agent-web/.../CombinedE2ETest.java`：断言 `conclusion.rootCauseId == expected.rootCauseId`。
- 受影响测试：InvestigationCoordinatorTest、EvidenceTimelinePersistenceTest、LogEvidenceInvestigationGuardTest。
- E01 fixture：source（仅 insert 为抛异常点）、logs.txt、recorded-drain3.json、incident.json、workspace/AssetService.java；真实 asset-service 源码同步。

## 二、负向测试（证明不能自证通过）

`ConclusionEvaluatorTest.wrongRootCauseIdFails`：EvidenceBundle 同时含 AssetRepository.insert 与 AssetService.create，expected.rootCauseId=AssetRepository.insert，Conclusion.rootCauseId 故意返回 AssetService.create → 评估失败，报告 `ROOT_CAUSE_MISMATCH:AssetService.create`。

## 三、测试数量

**mvn clean verify：BUILD SUCCESS，0 failure，101 测试，4 跳过**
（adapter 19 + core 54 + web 28）

跳过：CodeWorkspaceTest symlink（Windows）；CombinedE2ETest / Drain3IntegrationE2ETest / Log4jStacktraceE2ETest（真实外部服务默认跳过，前两者已显式执行通过）。

## 四、真实联合 E2E（已执行）

顺序：`mvn clean verify` → 显式 `DPOM_E2E_FULL=true + DEEPSEEK_API_KEY` 运行 CombinedE2ETest（另 DPOM_E2E_DRAIN3=true 运行 Drain3IntegrationE2ETest）。

- 执行时间：约 52s（latencyMs=48606）。
- 模型：deepseek-v4-pro。
- commit：e01abc。
- investigationStatus：COMPLETED。
- resultType：ROOT_CAUSE_FOUND。
- conclusion.rootCauseId：AssetRepository.insert。
- expected.rootCauseId：AssetRepository.insert。
- VERIFIED source evidence：code-5（symbol 命中 AssetRepository.insert、AssetService.create）。
- expectedSymbolsMatched：["AssetRepository.insert","AssetService.create"]。
- 真实 logEvidenceIds：["ev-2"]；sourceEvidenceIds：["code-5"]。

机器可读结果文件：`agent-web/target/e2e-results/combined-e2e.json`（mtime 2026/8/15 0:40:10）、`agent-web/target/e2e-results/drain3-e2e.json`（mtime 2026/8/15 0:40:10）。

## 五、边界

No RAG/Embedding/Vector DB；无 arbitrary shell；无自动生产执行；单实例 Java Web；未实现下一阶段功能。

## 六、结论

P1 阻塞项已修复并通过单元/负向/真实 E2E 验证。本 Change **不归档**，停止等待独立验收。
