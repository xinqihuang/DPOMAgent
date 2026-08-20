export function createApiClient(configuredBaseUrl, fetchImplementation = globalThis.fetch.bind(globalThis)) {
  const baseUrl = validateBaseUrl(configuredBaseUrl);

  async function request(path, options = {}) {
    const url = operationUrl(baseUrl, path);
    const method = options.method ?? "GET";
    const headers = {Accept: "application/json"};
    if (options.body !== undefined) headers["Content-Type"] = "application/json";
    if (method !== "GET") headers["Idempotency-Key"] = crypto.randomUUID();
    let response;
    try {
      response = await fetchImplementation(url, {
        method,
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
        credentials: "omit",
        cache: "no-store",
        referrerPolicy: "no-referrer"
      });
    } catch (cause) {
      throw clientError("NETWORK_ERROR", "无法连接 Change Guard，请检查地址、网络和 CORS 配置", cause);
    }
    const payload = await readPayload(response);
    if (!response.ok) {
      throw clientError(payload?.code ?? `HTTP_${response.status}`,
        payload?.message ?? "Change Guard 请求失败", undefined, response.status);
    }
    return payload;
  }

  return Object.freeze({
    baseUrl,
    create: body => request("/api/v1/operations", {method: "POST", body}),
    approve: (id, body) => request(`/api/v1/operations/${safeId(id)}/approvals`, {method: "POST", body}),
    shield: id => request(`/api/v1/operations/${safeId(id)}/shield`, {method: "POST"}),
    restore: id => request(`/api/v1/operations/${safeId(id)}/restore`, {method: "POST"}),
    retry: id => request(`/api/v1/operations/${safeId(id)}/restore/retry`, {method: "POST"}),
    details: id => request(`/api/v1/operations/${safeId(id)}`),
    audit: id => request(`/api/v1/operations/${safeId(id)}/audit`)
  });
}

export function validateBaseUrl(value) {
  if (value === "/change-guard-api") return value;
  let url;
  try {
    url = new URL(String(value ?? ""));
  } catch (cause) {
    throw clientError("INVALID_API_CONFIG", "Change Guard API 地址未配置", cause);
  }
  const localHttp = url.protocol === "http:" && ["localhost", "127.0.0.1"].includes(url.hostname);
  if ((url.protocol !== "https:" && !localHttp) || url.username || url.password || url.search || url.hash) {
    throw clientError("INVALID_API_CONFIG", "Change Guard API 必须使用 HTTPS；本地开发仅允许 localhost HTTP");
  }
  return url.href.replace(/\/$/, "");
}

function operationUrl(baseUrl, path) {
  if (!path.startsWith("/api/v1/operations") || path.includes("..") || path.includes("://")) {
    throw clientError("INVALID_API_PATH", "拒绝非 Change Guard API 路径");
  }
  return `${baseUrl}${path}`;
}

function safeId(value) {
  const id = String(value ?? "").trim();
  if (!/^[0-9a-fA-F-]{36}$/.test(id)) throw clientError("INVALID_OPERATION_ID", "操作 ID 格式无效");
  return encodeURIComponent(id);
}

async function readPayload(response) {
  if (response.status === 204) return null;
  const type = response.headers.get("content-type") ?? "";
  if (!type.includes("application/json")) return null;
  return response.json();
}

function clientError(code, message, cause, status) {
  const error = new Error(message, cause ? {cause} : undefined);
  error.code = code;
  error.status = status;
  return error;
}
