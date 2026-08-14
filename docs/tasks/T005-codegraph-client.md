# T005 — DPOMCodeGraph Client
## Goal
封装 DPOMCodeGraphService。
## Capabilities
resolveSnapshot、getSnapshot、findSymbol、findCallers、findCallees、findCallChain、findClassHierarchy。
## Requirements
Spring RestClient；timeout；remote DTO→internal DTO；commit 绑定；snapshot 非 READY 明确错误。
## Acceptance
本地 mock 验证 READY/NOT_FOUND/NOT_READY/timeout/query error。
