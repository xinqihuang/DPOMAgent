# T006 — Controlled Code Workspace
## Goal
让 LLM 直接在正确 Snapshot 目录搜索/阅读代码。
## Tools
list_files, search_text, read_source。
## Requirements
allowed roots；toRealPath；防 ../、绝对路径、symlink escape；max lines/bytes/hits。
V1 优先 Java NIO；若使用 rg，只能固定 executable + 参数数组。
## Critical
不得提供 execute_shell。
## Acceptance
正常 list/search/read；三类 path escape 拒绝；超大源码限制生效。
