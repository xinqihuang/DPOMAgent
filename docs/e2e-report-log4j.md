# DPOMAgent 端到端诊断报告（log4j2 异常堆栈）

## 调查 #137 概况（修复后重跑）
- 状态：COMPLETED（ROOT_CAUSE_FOUND，找到根因）
- 服务：log4j-core / 环境：prod / 发布：2.25.x / 提交：7cab23ba
- 真实链路：DeepSeek（deepseek-v4-pro）+ CodeGraphContext MCP（SSE）+ Drain3 MCP（SSE）+ MySQL + Redis
- 用例：Log4jStacktraceE2ETest，Tests run: 1, Failures: 0, Errors: 0（BUILD SUCCESS）

## 症状（模拟的异常堆栈）
```
java.lang.IllegalStateException: ConfigurationFactory returned no configuration
    at org.apache.logging.log4j.core.config.ConfigurationFactory.getConfiguration(ConfigurationFactory.java:305)
    at org.apache.logging.log4j.core.LoggerContext.reconfigure(LoggerContext.java:617)
    at org.apache.logging.log4j.core.impl.Log4jContextFactory.getContext(Log4jContextFactory.java:55)
```

## 调查步骤（7 步，仅追加）
1. START：开始调查
2. HYPOTHESIS：形成 5 条候选假设
3. TOOL：read_source（ConfigurationFactory.java，startLine=245，命中第 304 行 return null）
4. TOOL：search_text（搜索验证，命中为空）
5. TOOL：search_text（再次验证）
6. HYPOTHESIS/…（推进）
7. 结论：ROOT_CAUSE_FOUND

## 假设（5 条）
1. ConfigurationFactory.getConfiguration 内部逻辑缺陷：所有已注册 factory 均未匹配时错误返回 null，触发 IllegalStateException
2. 上游调用/状态错误：LoggerContext.reconfigure 传入的配置源（URI/config location）为空或路径错误
3. 配置/资源未初始化：log4j2 配置文件缺失、路径错误或 classpath 资源未打包，导致所有 factory 的 isActive 为 false
4. 版本/依赖不匹配：log4j-core 与 log4j-api 版本不一致或多版本冲突
5. 并发/时序：LoggerContext 初始化与工厂注册（PluginManager）存在竞态

## 观察（证据，3 条）
1. [workspace] ConfigurationFactory.java 第 245 行起源码（含行号），定位到 getConfiguration 第 304 行 return null 分支
2. [workspace] search_text 命中：[]（验证性搜索）
3. [workspace] search_text 命中：[]（验证性搜索）

## 结论
- resultType：ROOT_CAUSE_FOUND
- rootCause：ConfigurationFactory.getConfiguration(LoggerContext, String, URI) 在配置源不可用时命中空值分支：
  当 configLocation 为 null，或 ConfigurationSource.fromUri(configLocation) 返回 null 时，方法在第 304 行 return null；
  该 null 使上层无法从任何 factory 取得配置并抛出 IllegalStateException("ConfigurationFactory returned no configuration")。
  触发条件：LoggerContext.reconfigure 未获得有效 log4j2 配置源，典型为配置文件缺失、路径错误或 classpath 资源未打包。
- summary：Log4jContextFactory.getContext 首次创建 LoggerContext 时触发 reconfigure；reconfigure 传入的配置位置不可用
  （null 或无法解析为 ConfigurationSource），ConfigurationFactory.getConfiguration 命中第 304 行 return null 分支返回 null，
  框架未回退到默认配置而抛出 IllegalStateException。

## 修复说明（本轮针对“诊断没逻辑”的三处根因）
1. read_source 只返回前 200 行 → 新增 startLine 参数，按「行号-60」读取堆栈抛出点附近方法体，并给每行加「行号|」前缀。
2. 上下文用 JSON 转义源码（换行/引号被转义成一长串）→ 改为可读文本：假设带 [id=..][状态]，观察原文呈现源码。
3. 提示词强化：首轮必须先形成假设；读完源码必须更新假设或直接结论，禁止反复 read_source 同一文件。
