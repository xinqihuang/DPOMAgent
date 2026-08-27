# Progress capacity and retention operations

> Historical DPOMBase progress runbook. Current DPOMAgent progress operations are governed by the active
> realignment change and repository-level runbooks.

Keep the API disabled until Investigation schema readiness, a strong read token, page/client/buffer bounds,
connection duration, heartbeat, and poll interval all validate. Alert on rejected SSE capacity and repeated
retention resynchronization, never on investigation ids. Scale only within the deployment-reviewed maximum.

Retention removes whole old progress rows according to the environment policy while preserving the current
snapshot and at least the declared resume window. Before deletion, record oldest/latest sequences and counts.
After deletion, verify an older `Last-Event-ID` returns `RETENTION_GAP`, a retained cursor resumes monotonically,
and Kafka projection from retained rows has the same sequence/state/digest. Never delete Investigation state,
audit, publication intents, or evidence solely to repair an SSE client.

When capacity is exhausted, reject new streams with `SSE_CAPACITY_EXHAUSTED`; do not block or cancel diagnosis.
Clients retry with jitter and their last accepted sequence. A slow client is closed at the configured duration;
it then resumes or fetches a snapshot if the retention window moved.
