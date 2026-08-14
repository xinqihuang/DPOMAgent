# T010 — Diagnostic Script Artifact
## Goal
证据不足时生成给 SRE 执行的 Shell/Python/只读 SQL。
## Metadata
purpose, hypothesesToValidate, language, riskLevel, readOnly, expectedOutput, instructions, content。
## Policy
READ_ONLY_DIAGNOSTIC 禁止 rm/kill/restart/systemctl restart/kubectl delete/UPDATE/DELETE/INSERT/DDL 等。
实现保守 ScriptPolicyValidator；不要声称静态检测绝对安全。
## Feedback API
POST /investigations/{id}/scripts/{scriptId}/result
结果转 ArtifactRef+Observation，恢复 Investigation。
## Acceptance
安全脚本可生成；UPDATE 型 read-only 被拒绝；回传结果推动 WAITING_FOR_HUMAN→RESEARCHING。
