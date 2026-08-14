# T008 — DPOMBaseMCP Runtime Evidence
## Goal
接入运行时证据。
## Initial Capabilities
优先 APM trace、application logs、alarm/event summary、metrics；具体工具名以 DPOMBaseMCPServer 现有契约为准。
## Requirements
RuntimeEvidenceClient；远端 DTO 隔离；统一 ArtifactRef/ObservationInput；timeout/error；不得编造 service/env/trace 标识。
## Acceptance
本地 mock 覆盖 success/empty/timeout/error。
