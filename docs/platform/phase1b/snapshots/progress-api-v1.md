# DPOMBase Portal progress API v1 snapshot

All routes are default-off and require `Authorization: Bearer <independent-read-token>`.

| Method | Path | Success | Bounded behavior |
|---|---|---|---|
| GET | `/api/v1/investigations/{id}` | safe authoritative snapshot | no evidence/model/budget body |
| GET | `/api/v1/investigations/{id}/progress?after={n}&limit={n}` | oldest/latest/next cursor and ordered records | server maximum 100 |
| GET | `/api/v1/investigations/{id}/progress/stream` | `text/event-stream` | `Last-Event-ID`, max clients, buffer, heartbeat and duration |

Stable errors are `UNAUTHORIZED`, `INVESTIGATION_NOT_FOUND`, `INVALID_CURSOR`, `RETENTION_GAP`, and
`SSE_CAPACITY_EXHAUSTED`. `RETENTION_GAP` sets `resynchronize=true`; the client must fetch the snapshot.
There are no mutation routes.

