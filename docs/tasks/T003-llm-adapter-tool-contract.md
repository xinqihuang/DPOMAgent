# T003 — LLM Adapter & Tool Contract
## Goal
建立与具体模型无关的 ModelClient。
## Interfaces
ModelClient, ModelTurnRequest, ModelTurnResult, ToolDefinition, ToolInvocation, ToolResult。
## Requirements
Provider 实现在 agent-adapter-llm；Core 不 import Provider SDK DTO；支持 tool calling；记录 model/latency/token（可空）；timeout/error mapping；测试用 FakeModelClient。
## Acceptance
FakeModelClient 覆盖普通回答、tool call、tool result 后继续、timeout/error。
