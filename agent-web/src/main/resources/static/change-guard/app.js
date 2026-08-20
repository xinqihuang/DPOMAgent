import {createApiClient} from "./api-client.js?v=20260819-4";
import {auditEntry, availableActions, ruleSelector, sourceLabel, statusTone} from "./state.js?v=20260819-4";

const elements = Object.fromEntries(Array.from(document.querySelectorAll("[id]")).map(item => [item.id, item]));
let api = null;
let current = null;

initialize();

function initialize() {
  configureApi();
  seedTimes();
  ["AOM_V4", "UNIFIED_APM_AOM_V4", "CES_V2"].forEach(addRuleRow);
  bindEvents();
  renderOperation(null);
}

function configureApi() {
  try {
    api = createApiClient(window.CHANGE_GUARD_CONFIG?.apiBaseUrl);
  } catch (error) {
    notify(error.message, "danger");
  }
}

function seedTimes() {
  const now = new Date();
  const start = new Date(now.getTime() + 10 * 60_000);
  const end = new Date(now.getTime() + 70 * 60_000);
  const deadline = new Date(now.getTime() + 90 * 60_000);
  elements["window-start"].value = localDateTime(start);
  elements["window-end"].value = localDateTime(end);
  elements["restore-deadline"].value = localDateTime(deadline);
  elements["approval-expiry"].value = localDateTime(end);
}

function bindEvents() {
  elements["add-rule"].addEventListener("click", () => addRuleRow("AOM_V4"));
  elements["create-form"].addEventListener("submit", createOperation);
  elements["lookup-form"].addEventListener("submit", loadOperation);
  elements["approve-button"].addEventListener("click", approveOperation);
  elements["shield-button"].addEventListener("click", openShieldDialog);
  elements["restore-button"].addEventListener("click", () => runAction("restore"));
  elements["retry-button"].addEventListener("click", () => runAction("retry"));
  elements["refresh-audit"].addEventListener("click", refreshAudit);
  elements["confirm-ticket"].addEventListener("input", validateShieldConfirmation);
  elements["shield-dialog"].addEventListener("close", confirmShield);
}

async function createOperation(event) {
  event.preventDefault();
  await perform("正在读取并冻结规则清单…", async () => {
    const payload = {
      changeTicket: elements["change-ticket"].value.trim(),
      windowStart: iso(elements["window-start"].value),
      windowEnd: iso(elements["window-end"].value),
      restoreDeadline: iso(elements["restore-deadline"].value),
      rules: collectRules()
    };
    current = await api.create(payload);
    elements["operation-id"].value = current.operation.id;
    renderOperation(current);
    notify("预检完成，精确清单已冻结并等待独立审批。", "success");
  });
}

async function loadOperation(event) {
  event.preventDefault();
  await perform("正在加载操作详情…", async () => {
    current = await api.details(elements["operation-id"].value);
    renderOperation(current);
    notify("操作详情已刷新。", "success");
  });
}

async function approveOperation() {
  await perform("正在提交审批…", async () => {
    current = await api.approve(operationId(), {
      manifestDigest: current.operation.manifestDigest,
      expiresAt: iso(elements["approval-expiry"].value)
    });
    renderOperation(current);
    notify("审批已记录。执行人可在确认精确范围后屏蔽。", "success");
  });
}

function openShieldDialog() {
  const operation = current.operation;
  const sources = groupSources(current.rules);
  elements["dialog-summary"].replaceChildren(
    summaryItem("操作 ID", operation.id),
    summaryItem("变更单", operation.changeTicket),
    summaryItem("规则范围", sources),
    summaryItem("区域 / 项目", uniqueScopes(current.rules)),
    summaryItem("恢复截止", formatTime(operation.restoreDeadline))
  );
  elements["confirm-ticket"].value = "";
  elements["confirm-shield"].disabled = true;
  elements["shield-dialog"].showModal();
  elements["confirm-ticket"].focus();
}

function validateShieldConfirmation() {
  elements["confirm-shield"].disabled = elements["confirm-ticket"].value !== current?.operation?.changeTicket;
}

async function confirmShield() {
  if (elements["shield-dialog"].returnValue !== "default") return;
  await runAction("shield");
}

async function runAction(action) {
  const labels = {shield: "正在按 AOM → APM → CES 顺序屏蔽…", restore: "正在恢复原始规则状态…", retry: "正在重试未恢复规则…"};
  await perform(labels[action], async () => {
    current = await api[action](operationId());
    renderOperation(current);
    const state = current.operation.state;
    notify(action === "shield" && state === "SHIELDED" ? "全部目标已验证停用，可以开始实施。" : `操作已更新为 ${state}。`,
      state === "RESTORE_PARTIAL" || state === "COMPENSATION_REQUIRED" ? "warning" : "success");
  });
}

async function refreshAudit() {
  await perform("正在刷新审计时间线…", async () => {
    const audit = await api.audit(operationId());
    renderAudit(audit);
    notify("审计时间线已刷新。", "success");
  });
}

async function perform(message, work) {
  if (!api) {
    notify("Change Guard API 配置无效，请联系部署管理员。", "danger");
    return;
  }
  setBusy(true);
  notify(message, "info");
  try {
    await work();
  } catch (error) {
    handleError(error);
  } finally {
    setBusy(false);
    updateActions();
  }
}

function handleError(error) {
  notify(`${error.code ? `${error.code} · ` : ""}${error.message}`, "danger");
}

function addRuleRow(source) {
  const row = elements["rule-row-template"].content.firstElementChild.cloneNode(true);
  row.querySelector(".rule-source").value = source;
  row.querySelector(".remove-rule").addEventListener("click", () => row.remove());
  elements["rule-editor"].append(row);
}

function collectRules() {
  const rows = Array.from(elements["rule-editor"].querySelectorAll(".rule-row"));
  if (!rows.length) throw new Error("至少添加一条精确规则引用");
  return rows.map(row => ruleSelector({
    source: row.querySelector(".rule-source").value,
    region: row.querySelector(".rule-region").value,
    projectId: row.querySelector(".rule-project").value,
    enterpriseProjectId: row.querySelector(".rule-eps").value,
    upstreamRuleId: row.querySelector(".rule-id").value,
    expectedName: row.querySelector(".rule-name").value
  }));
}

function renderOperation(details) {
  current = details;
  const operation = details?.operation;
  const state = operation?.state ?? "未创建";
  elements["operation-status"].textContent = state;
  elements["operation-status"].className = `badge badge-${statusTone(operation?.state)}`;
  elements["summary-ticket"].textContent = operation?.changeTicket ?? "—";
  elements["summary-digest"].textContent = operation?.manifestDigest ?? "—";
  elements["summary-digest"].title = operation?.manifestDigest ?? "";
  elements["summary-rules"].textContent = details?.rules?.length ? groupSources(details.rules) : "—";
  elements["summary-deadline"].textContent = formatTime(operation?.restoreDeadline);
  if (operation?.id) elements["operation-id"].value = operation.id;
  renderRules(details?.rules ?? [], details?.attempts ?? []);
  renderAudit(details?.auditEvents ?? []);
  updateActions();
}

function renderRules(rules, attempts) {
  elements["rules-body"].replaceChildren();
  if (!rules.length) {
    const row = document.createElement("tr");
    const cell = document.createElement("td");
    cell.colSpan = 7;
    cell.className = "empty-cell";
    cell.textContent = "预检后将在这里展示精确规则清单。";
    row.append(cell);
    elements["rules-body"].append(row);
    elements["rule-stats"].textContent = "0 条规则";
    return;
  }
  const requestIds = new Map(attempts.map(item => [item.ruleSnapshotId, item.upstreamRequestId]));
  rules.forEach(rule => {
    const row = document.createElement("tr");
    const identity = rule.identity;
    row.append(
      cell("来源", sourceLabel(identity.source)),
      ruleCell(identity.upstreamRuleName, identity.upstreamRuleId),
      cell("区域 / 项目", `${identity.region} / ${shorten(identity.projectId)}`),
      cell("原状态", rule.originalEnabled ? "● 已启用" : "○ 已停用"),
      cell("当前状态", rule.lastKnownEnabled ? "● 已启用" : "○ 已停用"),
      cell("步骤", rule.stepState),
      cell("Request ID", requestIds.get(rule.id) ?? "—")
    );
    elements["rules-body"].append(row);
  });
  const enabled = rules.filter(item => item.originalEnabled).length;
  elements["rule-stats"].textContent = `${rules.length} 条 · 原启用 ${enabled} · 原停用 ${rules.length - enabled}`;
}

function renderAudit(events) {
  elements["audit-list"].replaceChildren();
  if (!events.length) {
    const item = document.createElement("li");
    item.className = "timeline-empty";
    item.textContent = current ? "当前操作尚无审计事件。" : "加载操作后查看有序生命周期事件。";
    elements["audit-list"].append(item);
    return;
  }
  events.forEach(event => {
    const entry = auditEntry(event);
    const item = document.createElement("li");
    item.append(span(formatTime(entry.time), "timeline-time"), span(entry.type, "timeline-event"),
      span(entry.detail, "timeline-detail"));
    elements["audit-list"].append(item);
  });
}

function updateActions() {
  const actions = availableActions(current);
  elements["approve-button"].disabled = !actions.approve;
  elements["shield-button"].disabled = !actions.shield;
  elements["restore-button"].disabled = !actions.restore;
  elements["retry-button"].disabled = !actions.retry;
  elements["refresh-audit"].disabled = !current?.operation?.id;
  const state = current?.operation?.state;
  elements["action-hint"].textContent = actions.restore
    ? "该操作仍有规则需要恢复；恢复不需要新的审批。"
    : state === "SHIELDED" ? "规则已屏蔽，请在实施完成后立即恢复。" : "动作严格跟随服务端状态开放。";
}

function setBusy(busy) {
  document.body.setAttribute("aria-busy", String(busy));
  document.querySelectorAll("button").forEach(button => {
    if (busy) button.dataset.wasDisabled = String(button.disabled);
    if (busy) button.disabled = true;
    else if (button.dataset.wasDisabled === "false") button.disabled = false;
    if (!busy) delete button.dataset.wasDisabled;
  });
}

function notify(message, tone) {
  elements["live-region"].textContent = message;
  elements["live-region"].className = `notice notice-${tone}`;
}

function cell(label, value) {
  const item = document.createElement("td");
  item.dataset.label = label;
  item.textContent = value ?? "—";
  return item;
}

function ruleCell(name, id) {
  const item = document.createElement("td");
  item.dataset.label = "规则";
  const title = span(name || "未命名规则", "rule-name");
  const code = span(id, "rule-id");
  item.append(title, code);
  return item;
}

function span(value, className) {
  const item = document.createElement("span");
  item.className = className;
  item.textContent = value ?? "—";
  return item;
}

function summaryItem(label, value) {
  const wrapper = document.createElement("div");
  const term = document.createElement("dt");
  const detail = document.createElement("dd");
  term.textContent = label;
  detail.textContent = value ?? "—";
  wrapper.append(term, detail);
  return wrapper;
}

function groupSources(rules) {
  const counts = new Map();
  rules.forEach(rule => counts.set(sourceLabel(rule.identity.source), (counts.get(sourceLabel(rule.identity.source)) ?? 0) + 1));
  return Array.from(counts, ([name, count]) => `${name} ${count}`).join(" · ");
}

function uniqueScopes(rules) {
  return Array.from(new Set(rules.map(rule => `${rule.identity.region} / ${shorten(rule.identity.projectId)}`))).join("；");
}

function operationId() {
  return current?.operation?.id ?? elements["operation-id"].value;
}

function iso(value) {
  if (!value) throw new Error("请填写完整时间");
  return new Date(value).toISOString();
}

function localDateTime(date) {
  const shifted = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return shifted.toISOString().slice(0, 16);
}

function formatTime(value) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("zh-CN", {dateStyle: "medium", timeStyle: "short"}).format(new Date(value));
}

function shorten(value) {
  if (!value || value.length < 12) return value ?? "—";
  return `${value.slice(0, 6)}…${value.slice(-4)}`;
}
