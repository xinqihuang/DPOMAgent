# energy-platform-demo

最小可编译的 Spring 风格能源管理平台示例源码，供 DPOMAgent 诊断回归套件使用。

## 服务与故障点
- asset-service：AssetController → AssetService(create, @Transactional) → AssetRepository.insert —— E01 设备创建事务回滚，根因 AssetRepository.insert。
- telemetry-service：BatchPublisher.flush —— E03 遥测批处理部分丢失，根因 BatchPublisher.flush。
- gateway-service：DownstreamClient.call —— E05 下游超时重试风暴，根因 DownstreamClient.call。

## CodeGraphContext 索引
对每个服务的 src/main/java 目录执行（或对整体目录）：
```
cgc index test-fixtures/energy-platform-demo/asset-service/src/main/java
cgc index test-fixtures/energy-platform-demo/telemetry-service/src/main/java
cgc index test-fixtures/energy-platform-demo/gateway-service/src/main/java
```

## 回归运行
真实回归需 Drain3(8100)、CodeGraphContext(8080)、DeepSeek key，并已索引对应服务：
```
DPOM_E2E_FULL=true mvn -pl agent-web -am test -Dtest=DiagnosticRegressionE2ETest
```

DPOMAgent 仍为单实例 Java Web；本目录仅为静态示例源码，不提供第二个 Web 控制面。
