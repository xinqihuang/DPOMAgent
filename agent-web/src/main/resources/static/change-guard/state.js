const RESTORABLE = new Set([
  "SHIELDING",
  "SHIELDED",
  "RESTORE_REQUIRED",
  "RESTORING",
  "RESTORE_PARTIAL",
  "COMPENSATING",
  "COMPENSATION_REQUIRED"
]);

const RETRYABLE = new Set([
  "RESTORE_REQUIRED",
  "RESTORE_PARTIAL",
  "COMPENSATION_REQUIRED"
]);

export function availableActions(operation) {
  const status = operation?.operation?.state ?? operation?.state ?? "UNKNOWN";
  return Object.freeze({
    approve: status === "AWAITING_APPROVAL",
    shield: status === "APPROVED",
    restore: RESTORABLE.has(status),
    retry: RETRYABLE.has(status)
  });
}

export function statusTone(status) {
  if (["RESTORED", "SHIELDED", "APPROVED"].includes(status)) return "success";
  if (["PRECHECK_FAILED", "SHIELD_FAILED", "RESTORE_PARTIAL", "COMPENSATION_REQUIRED"].includes(status)) {
    return "danger";
  }
  if (["SHIELDING", "RESTORING", "COMPENSATING", "RESTORE_REQUIRED"].includes(status)) return "warning";
  return "neutral";
}

export function auditEntry(event) {
  const before = event?.beforeState;
  const after = event?.afterState;
  const transition = before && after ? `${before} → ${after}` : after ?? "";
  const detail = [event?.actor, transition, event?.ruleKey, event?.details].filter(part => part).join(" · ");
  return Object.freeze({
    time: event?.createdAt ?? null,
    type: event?.eventType ?? "UNKNOWN_EVENT",
    detail: detail || "已记录"
  });
}

export function sourceLabel(source) {
  return ({
    AOM_V4: "AOM",
    UNIFIED_APM_AOM_V4: "统一 APM",
    CES_V2: "CES"
  })[source] ?? source ?? "未知";
}
