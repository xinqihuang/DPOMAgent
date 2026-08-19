# 告警变更管控 UI 部署与安全说明

## 访问入口

DPOMAgent 只托管静态页面：

```text
http(s)://<dpom-agent-host>/change-guard/index.html
```

页面通过 DPOMAgent 的固定同源入口访问独立 HuaweiCloudAlarmChangeGuard 服务。转发入口只允许 `/api/v1/operations`，不接受动态目标；页面不提供登录或会话输入，DPOMAgent 也不持有 AK/SK。

## API 地址

页面默认使用 DPOMAgent 同源转发路径：

```javascript
window.CHANGE_GUARD_CONFIG = Object.freeze({
  apiBaseUrl: "/change-guard-api"
});
```

下游 Change Guard 地址由服务端属性 `dpom.change-guard.base-url` 固定配置（见 `agent-web/src/main/resources/application.yml`），
可用环境变量 `DPOM_CHANGE_GUARD_BASE_URL` 覆盖，本地默认是 `http://localhost:8081`；生产部署必须指向 HTTPS 地址。
页面不允许操作者改写下游目标。

## 同源转发

浏览器只访问 DPOMAgent 同源路径，因此无需开放浏览器到 Change Guard 的跨域访问。转发仅接受：

```text
GET  /change-guard-api/api/v1/operations/**
POST /change-guard-api/api/v1/operations/**
```

其他目标和路径不会被转发。业务状态、审批、屏蔽、恢复及审计仍全部由 Change Guard 处理。

## CSP 与凭据边界

页面自带 CSP，默认只加载同源静态资源并只允许 HTTPS `connect-src`。生产反向代理应同步设置响应头版本 CSP，并至少包含：

```text
default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'
```

页面不提供 Bearer Token、AK 或 SK 输入，也不自行添加 `Authorization` 请求头。不得把 AK/SK、Token 或签名材料写入 `config.js`。

## 本地检查

使用测试 classpath 的 H2 配置启动，避免依赖本地 MySQL：

```powershell
mvn -pl agent-web -am package -DskipTests
mvn -pl agent-web spring-boot:run -Dspring-boot.run.useTestClasspath=true
```

本地页面通过 DPOMAgent 同源入口连接 `http://localhost:8081`。是否允许真实云写操作仍由 Change Guard 的写开关和 region/project allowlist 控制。
