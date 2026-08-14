# T007 — Stacktrace Code Investigation
## Goal
跑通明确 Java 异常堆栈。
## Flow
stacktrace→提取 class/method/file/line→resolve snapshot→read source→必要时 callers/callees→Observation→Hypothesis→Conclusion。
## Fixture
Spring fixture：Controller→Service→Repository，并构造明确异常。
## Acceptance
输出 commit、文件/行号；至少 source read；需要时 graph query；RCA 不可只引用 CGC 文本。
