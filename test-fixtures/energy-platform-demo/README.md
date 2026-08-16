# energy-platform-demo

最小可编译的 Spring 风格能源管理平台示例源码，供 DPOMAgent 诊断回归套件使用。

## 服务与故障点
- asset-service：AssetController → AssetService(create, @Transactional) → AssetRepository.insert —— E01 设备创建事务回滚，根因 AssetRepository.insert。
- telemetry-service：BatchPublisher.flush —— E03 遥测批处理部分丢失，根因 BatchPublisher.flush。
- gateway-service：DownstreamClient.call —— E05 下游超时重试风暴，根因 DownstreamClient.call。

## CodeGraph 索引
对每个服务目录执行（或对整体目录）：
```
codegraph init test-fixtures/energy-platform-demo/asset-service
codegraph init test-fixtures/energy-platform-demo/telemetry-service
codegraph init test-fixtures/energy-platform-demo/gateway-service
```

## 回归运行
真实回归需 Drain3(8100)、CodeGraph stdio MCP（固定版本，见 dpom.codegraph.version）、DeepSeek key，并已索引对应服务：
```
DPOM_E2E_FULL=true mvn -pl agent-web -am test -Dtest=DiagnosticRegressionE2ETest
```

真实 CodeGraph stdio E2E 由 `DPOM_CODEGRAPH_E2E=true` 显式启用；本机未安装固定版本时报 NOT_EXECUTED。

DPOMAgent 仍为单实例 Java Web；本目录仅为静态示例源码，不提供第二个 Web 控制面。
