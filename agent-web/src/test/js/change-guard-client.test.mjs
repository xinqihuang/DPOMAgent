import assert from "node:assert/strict";
import test from "node:test";
import {createApiClient, validateBaseUrl} from "../../main/resources/static/change-guard/api-client.js";
import {auditEntry, availableActions} from "../../main/resources/static/change-guard/state.js";

test("rejects non-https and credential-bearing API targets", () => {
  assert.throws(() => validateBaseUrl("http://change-guard.example.test"), /HTTPS/);
  assert.throws(() => validateBaseUrl("https://user:pass@change-guard.example.test"), /HTTPS/);
  assert.equal(validateBaseUrl("http://localhost:8081"), "http://localhost:8081");
  assert.equal(validateBaseUrl("/change-guard-api"), "/change-guard-api");
});

test("omits authorization and adds a fresh idempotency key to every write", async () => {
  const requests = [];
  const fetchStub = async (url, options) => {
    requests.push({url, options});
    return jsonResponse(201, {operation: {id: "00000000-0000-0000-0000-000000000001"}});
  };
  const client = createApiClient("https://change-guard.example.test", fetchStub);
  await client.create({changeTicket: "CHG-1"});
  await client.create({changeTicket: "CHG-2"});

  assert.equal(requests[0].options.headers.Authorization, undefined);
  assert.notEqual(requests[0].options.headers["Idempotency-Key"], requests[1].options.headers["Idempotency-Key"]);
  assert.ok(requests.every(item => item.url === "https://change-guard.example.test/api/v1/operations"));
});

test("maps authentication failures without managing a browser session", async () => {
  const client = createApiClient("https://change-guard.example.test",
    async () => jsonResponse(401, {code: "UNAUTHORIZED", message: "expired"}));
  await assert.rejects(client.details("00000000-0000-0000-0000-000000000001"), /expired/);
});

test("maps known states and keeps unknown states fail-closed", () => {
  assert.equal(availableActions({operation: {state: "APPROVED"}}).shield, true);
  assert.equal(availableActions({operation: {state: "SHIELDED"}}).restore, true);
  assert.equal(availableActions({operation: {state: "RESTORE_PARTIAL"}}).retry, true);
  assert.deepEqual(availableActions({operation: {state: "FUTURE_STATE"}}), {
    approve: false,
    shield: false,
    restore: false,
    retry: false
  });
});

test("maps audit events using the fields Change Guard actually returns", () => {
  const entry = auditEntry({
    eventType: "RULE_DISABLE_FAILED",
    actor: "local-executor",
    ruleKey: "AOM_V4|cn-north-4|proj|rule-1",
    beforeState: "DISABLE_PENDING",
    afterState: "DISABLE_FAILED",
    upstreamRequestId: "req-9",
    details: "missing provider result",
    createdAt: "2026-08-19T06:00:00Z"
  });

  assert.equal(entry.time, "2026-08-19T06:00:00Z");
  assert.equal(entry.type, "RULE_DISABLE_FAILED");
  assert.match(entry.detail, /local-executor/);
  assert.match(entry.detail, /DISABLE_PENDING → DISABLE_FAILED/);
  assert.match(entry.detail, /missing provider result/);
});

test("keeps audit rendering safe when optional audit fields are absent", () => {
  const entry = auditEntry({eventType: "OPERATION_CREATED", actor: "local-requester", afterState: "DRAFT"});

  assert.equal(entry.time, null);
  assert.equal(entry.detail, "local-requester · DRAFT");
  assert.deepEqual(auditEntry({}), {time: null, type: "UNKNOWN_EVENT", detail: "已记录"});
});

function jsonResponse(status, payload) {
  return {
    status,
    ok: status >= 200 && status < 300,
    headers: new Headers({"content-type": "application/json"}),
    json: async () => payload
  };
}
