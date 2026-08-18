/* @forgeops/feedback-bundle v0.1.0 (build 05d6a22) - self-contained ESM, react external.
 * Source: forge-ops monorepo sdk/forgeops-feedback-{core,dom,react}. Sync by re-running scripts/build-integration-bundle.mjs */

// sdk/forgeops-feedback-react/src/index.tsx
import { useEffect, useRef } from "react";

// sdk/forgeops-feedback-core/src/ulid.ts
var ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
function ulid() {
  const now = Date.now();
  let time = now;
  let out = "";
  for (let i = 0; i < 10; i++) {
    out = ENCODING[time % 32] + out;
    time = Math.floor(time / 32);
  }
  const bytes = new Uint8Array(10);
  if (typeof crypto !== "undefined" && crypto.getRandomValues) {
    crypto.getRandomValues(bytes);
  } else {
    for (let i = 0; i < 10; i++) bytes[i] = Math.floor(Math.random() * 256);
  }
  let entropy = "";
  for (const b of bytes) entropy += ENCODING[b % 32];
  return out + entropy;
}

// sdk/forgeops-feedback-core/src/collector.ts
var RequestContextCollector = class {
  constructor(bufferSize = 50) {
    this.buffer = [];
    this.consoleErrors = [];
    this.bufferSize = 50;
    this.originalFetch = null;
    this.consoleErrorPatched = false;
    this.bufferSize = bufferSize;
  }
  start() {
    this.patchFetch();
    this.patchConsoleError();
  }
  stop() {
    if (this.originalFetch) {
      window.fetch = this.originalFetch;
      this.originalFetch = null;
    }
  }
  patchFetch() {
    if (this.originalFetch || typeof window === "undefined") return;
    const original = window.fetch.bind(window);
    this.originalFetch = window.fetch;
    window.fetch = async (input, init) => {
      var _a, _b;
      const method = ((init == null ? void 0 : init.method) || (input instanceof Request ? input.method : "GET")).toUpperCase();
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
      const requestId = input instanceof Request ? (_a = input.headers.get("X-Request-ID")) != null ? _a : ulid() : ulid();
      const start = performance.now();
      const headers = new Headers((init == null ? void 0 : init.headers) || (input instanceof Request ? input.headers : void 0));
      if (!headers.has("X-Request-ID")) headers.set("X-Request-ID", requestId);
      try {
        const response = await original(input, { ...init, headers });
        this.record({
          method,
          url,
          status: response.status,
          durationMs: Math.round(performance.now() - start),
          requestId: (_b = response.headers.get("X-Request-ID")) != null ? _b : requestId,
          time: (/* @__PURE__ */ new Date()).toISOString()
        });
        return response;
      } catch (err) {
        this.record({
          method,
          url,
          status: 0,
          durationMs: Math.round(performance.now() - start),
          requestId,
          time: (/* @__PURE__ */ new Date()).toISOString()
        });
        throw err;
      }
    };
  }
  patchConsoleError() {
    if (this.consoleErrorPatched || typeof console === "undefined") return;
    this.consoleErrorPatched = true;
    const original = console.error.bind(console);
    console.error = (...args) => {
      try {
        const text = args.map((a) => {
          if (a instanceof Error) return `${a.name}: ${a.message}`;
          if (typeof a === "string") return a;
          try {
            return JSON.stringify(a);
          } catch {
            return String(a);
          }
        }).join(" ").slice(0, 2e3);
        if (text) {
          this.consoleErrors.push(`[${(/* @__PURE__ */ new Date()).toISOString()}] ${text}`);
          if (this.consoleErrors.length > 10) this.consoleErrors.shift();
        }
      } catch {
      }
      original(...args);
    };
  }
  record(entry) {
    this.buffer.push(entry);
    if (this.buffer.length > this.bufferSize) this.buffer.shift();
  }
  /** 最近失败的请求（4xx/5xx/网络错误），供反馈自动附带。 */
  failedRequests(max = 10) {
    return this.buffer.filter((r) => r.status === void 0 || r.status >= 400 || r.status === 0).slice(-max);
  }
  recentRequests() {
    return [...this.buffer];
  }
  consoleErrorList() {
    return [...this.consoleErrors];
  }
  /** axios 项目：手动登记一条请求摘要（配合 attachAxios 使用）。 */
  recordAxios(summary) {
    this.record(summary);
  }
};
function attachAxios(axiosInstance, collector) {
  axiosInstance.interceptors.request.use((config) => {
    var _a;
    const headers = (_a = config.headers) != null ? _a : {};
    if (!headers["X-Request-ID"]) headers["X-Request-ID"] = ulid();
    config.headers = headers;
    return config;
  });
  axiosInstance.interceptors.response.use(
    (response) => {
      var _a, _b, _c, _d, _e, _f, _g;
      const headers = (_a = response.headers) != null ? _a : {};
      collector.recordAxios({
        method: String((_c = (_b = response.config) == null ? void 0 : _b.method) != null ? _c : "GET").toUpperCase(),
        url: String((_e = (_d = response.config) == null ? void 0 : _d.url) != null ? _e : ""),
        status: Number((_f = response.status) != null ? _f : 0),
        durationMs: headers["X-Duration-Ms"] ? Number(headers["X-Duration-Ms"]) : void 0,
        requestId: (_g = headers["X-Request-ID"]) != null ? _g : headers["x-request-id"],
        time: (/* @__PURE__ */ new Date()).toISOString()
      });
      return response;
    },
    (error) => {
      var _a, _b, _c, _d, _e, _f, _g, _h;
      const cfg = (_a = error == null ? void 0 : error.config) != null ? _a : {};
      const headers = (_c = (_b = error == null ? void 0 : error.response) == null ? void 0 : _b.headers) != null ? _c : {};
      collector.recordAxios({
        method: String((_d = cfg.method) != null ? _d : "GET").toUpperCase(),
        url: String((_e = cfg.url) != null ? _e : ""),
        status: Number((_g = (_f = error == null ? void 0 : error.response) == null ? void 0 : _f.status) != null ? _g : 0),
        requestId: (_h = headers["X-Request-ID"]) != null ? _h : headers["x-request-id"],
        time: (/* @__PURE__ */ new Date()).toISOString()
      });
      return Promise.reject(error);
    }
  );
}

// sdk/forgeops-feedback-core/src/gateway.ts
var ForgeOpsGatewayClient = class {
  constructor(baseUrl) {
    this.baseUrl = baseUrl;
  }
  async request(path, init) {
    const res = await fetch(`${this.baseUrl.replace(/\/$/, "")}${path}`, {
      headers: { "Content-Type": "application/json", ...(init == null ? void 0 : init.headers) || {} },
      ...init
    });
    if (!res.ok) {
      let message = `HTTP ${res.status}`;
      try {
        const body = await res.json();
        if (body == null ? void 0 : body.message) message = body.message;
      } catch {
      }
      throw new Error(message);
    }
    return res.json();
  }
  submitFeedback(payload) {
    return this.request("/api/v1/feedback", { method: "POST", body: JSON.stringify(payload) });
  }
  listMyFeedback(reporter, projectId) {
    const query = `reporter=${encodeURIComponent(reporter)}&projectId=${encodeURIComponent(projectId)}`;
    return this.request(`/api/v1/feedback?${query}`);
  }
  getFeedback(id) {
    return this.request(`/api/v1/feedback/${encodeURIComponent(id)}`);
  }
  comment(id, content) {
    return this.request(`/api/v1/feedback/${encodeURIComponent(id)}/comment`, {
      method: "POST",
      body: JSON.stringify({ content })
    });
  }
  verifyPass(id, verifierName, comment) {
    return this.request(`/api/v1/feedback/${encodeURIComponent(id)}/verify`, {
      method: "POST",
      body: JSON.stringify({ result: "PASS", verifierName, comment })
    });
  }
  verifyFail(id, verifierName, comment, extra) {
    return this.request(`/api/v1/feedback/${encodeURIComponent(id)}/reopen`, {
      method: "POST",
      body: JSON.stringify({ verifierName, comment, requests: (extra == null ? void 0 : extra.requests) || [], consoleErrors: (extra == null ? void 0 : extra.consoleErrors) || [] })
    });
  }
};

// sdk/forgeops-feedback-core/src/context.ts
var FeedbackCore = class {
  constructor(options) {
    this.reporterName = "";
    var _a;
    this.options = options;
    this.collector = new RequestContextCollector((_a = options.requestBufferSize) != null ? _a : 50);
    this.client = new ForgeOpsGatewayClient(options.gatewayUrl);
  }
  get enabled() {
    var _a;
    const allow = (_a = this.options.enabledEnvironments) != null ? _a : ["test", "uat", "staging"];
    return allow.includes(this.options.environment);
  }
  /** 上次提交人（「我的反馈」默认查询者）。 */
  get lastReporterName() {
    return this.reporterName;
  }
  setReporterName(name) {
    this.reporterName = name;
  }
  getReporter() {
    var _a, _b;
    const fromApp = (_b = (_a = this.options).getReporter) == null ? void 0 : _b.call(_a);
    if (fromApp == null ? void 0 : fromApp.name) return fromApp;
    return this.reporterName ? { name: this.reporterName } : null;
  }
  start() {
    if (this.enabled) this.collector.start();
  }
  buildSubmission(form) {
    var _a, _b, _c, _d;
    const opts = this.options;
    const frontend = ((_a = opts.getFrontendInfo) == null ? void 0 : _a.call(opts)) || {};
    const backend = ((_b = opts.getBackendInfo) == null ? void 0 : _b.call(opts)) || {};
    return {
      schemaVersion: "1.0",
      projectId: opts.projectId,
      type: form.type,
      title: form.title || void 0,
      description: form.description,
      expectedBehavior: form.expectedBehavior || void 0,
      steps: form.steps || void 0,
      note: form.note || void 0,
      reporter: (_c = this.getReporter()) != null ? _c : { name: form.reporterName },
      environment: opts.environment,
      page: {
        url: location.href,
        route: (_d = opts.getRouteName) == null ? void 0 : _d.call(opts),
        title: document.title
      },
      client: {
        userAgent: navigator.userAgent,
        platform: navigator.platform,
        language: navigator.language,
        screen: `${screen.width}x${screen.height}`,
        viewport: `${innerWidth}x${innerHeight}`
      },
      frontend,
      backend: backend.version || backend.commit ? backend : void 0,
      requests: this.collector.failedRequests(),
      consoleErrors: this.collector.consoleErrorList(),
      screenshot: form.screenshot,
      occurredAt: (/* @__PURE__ */ new Date()).toISOString()
    };
  }
};

// sdk/forgeops-feedback-dom/src/style.ts
var WIDGET_CSS = `
.forgeops-root { position: static; }
.forgeops-root, .forgeops-root * { box-sizing: border-box; font-family: -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif; }
.forgeops-fab {
  position: fixed; right: 24px; bottom: 24px; z-index: 2147483000;
  background: #2563eb; color: #fff; border: none; border-radius: 24px;
  padding: 10px 22px; font-size: 14px; cursor: pointer; box-shadow: 0 4px 14px rgba(37,99,235,.4);
}
.forgeops-fab:hover { background: #1d4ed8; }
.forgeops-panel {
  position: fixed; right: 24px; bottom: 76px; z-index: 2147483000;
  width: 420px; max-width: calc(100vw - 32px); max-height: min(78vh, 720px);
  background: #fff; border-radius: 12px; box-shadow: 0 12px 40px rgba(0,0,0,.18);
  display: flex; flex-direction: column; overflow: hidden;
}
.forgeops-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid #e5e7eb; }
.forgeops-title { font-weight: 600; font-size: 15px; color: #111827; }
.forgeops-close { background: none; border: none; cursor: pointer; font-size: 14px; color: #6b7280; padding: 4px 8px; }
.forgeops-tabs { display: flex; border-bottom: 1px solid #e5e7eb; }
.forgeops-tab { flex: 1; padding: 10px; background: none; border: none; font-size: 13px; color: #6b7280; cursor: pointer; border-bottom: 2px solid transparent; }
.forgeops-tab.active { color: #2563eb; border-bottom-color: #2563eb; font-weight: 600; }
.forgeops-body { padding: 14px 16px; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; font-size: 13px; color: #111827; }
.forgeops-field { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: #374151; }
.forgeops-field input, .forgeops-field select, .forgeops-field textarea {
  border: 1px solid #d1d5db; border-radius: 6px; padding: 7px 9px; font-size: 13px; background: #fff; width: 100%;
}
.forgeops-field input:focus, .forgeops-field textarea:focus, .forgeops-field select:focus { outline: 2px solid #93c5fd; border-color: #2563eb; }
.forgeops-required { color: #dc2626; }
.forgeops-details-toggle { cursor: pointer; color: #6b7280; font-size: 12px; }
.forgeops-pre {
  background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 6px; padding: 8px;
  font-size: 11px; max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-all;
  font-family: ui-monospace, Menlo, Consolas, monospace; margin: 4px 0 0;
}
.forgeops-submit { background: #2563eb; color: #fff; border: none; border-radius: 8px; padding: 10px; font-size: 14px; cursor: pointer; }
.forgeops-submit:disabled { background: #93c5fd; cursor: not-allowed; }
.forgeops-error { background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca; border-radius: 6px; padding: 8px; font-size: 12px; }
.forgeops-success { background: #f0fdf4; color: #15803d; border: 1px solid #bbf7d0; border-radius: 6px; padding: 8px; font-size: 12px; }
.forgeops-row { display: flex; gap: 8px; }
.forgeops-row input { flex: 1; border: 1px solid #d1d5db; border-radius: 6px; padding: 7px 9px; font-size: 13px; }
.forgeops-row button { background: #f3f4f6; border: 1px solid #d1d5db; border-radius: 6px; padding: 7px 12px; cursor: pointer; font-size: 13px; }
.forgeops-item { border: 1px solid #e5e7eb; border-radius: 8px; padding: 10px; cursor: pointer; }
.forgeops-item:hover { border-color: #93c5fd; background: #f8fafc; }
.forgeops-item-line { display: flex; justify-content: space-between; margin-bottom: 4px; }
.forgeops-item-id { color: #6b7280; font-size: 12px; }
.forgeops-empty { color: #9ca3af; text-align: center; padding: 20px 0; }
.forgeops-status { font-size: 11px; padding: 2px 8px; border-radius: 10px; white-space: nowrap; }
.forgeops-st-todo { background: #f3f4f6; color: #6b7280; }
.forgeops-st-doing { background: #eff6ff; color: #2563eb; }
.forgeops-st-verify { background: #fef3c7; color: #b45309; }
.forgeops-st-done { background: #f0fdf4; color: #15803d; }
.forgeops-st-info { background: #fdf2f8; color: #be185d; }
.forgeops-back { background: none; border: none; color: #2563eb; cursor: pointer; font-size: 13px; padding: 0; align-self: flex-start; }
.forgeops-detail-title { margin: 2px 0 6px; font-size: 14px; color: #111827; font-weight: 600; }
.forgeops-meta { font-size: 12px; color: #374151; word-break: break-all; }
.forgeops-meta a { color: #2563eb; }
.forgeops-timeline { display: flex; flex-direction: column; gap: 8px; max-height: 240px; overflow-y: auto; }
.forgeops-comment { border: 1px solid #e5e7eb; border-radius: 8px; padding: 8px 10px; }
.forgeops-comment-head { display: flex; justify-content: space-between; font-size: 11px; color: #6b7280; margin-bottom: 4px; gap: 8px; }
.forgeops-comment-body { font-size: 12px; white-space: pre-wrap; word-break: break-word; color: #111827; }
.forgeops-verify { display: flex; gap: 8px; margin-top: 4px; }
.forgeops-pass { flex: 1; background: #16a34a; color: #fff; border: none; border-radius: 8px; padding: 9px; cursor: pointer; font-size: 13px; }
.forgeops-fail { flex: 1; background: #dc2626; color: #fff; border: none; border-radius: 8px; padding: 9px; cursor: pointer; font-size: 13px; }
`;

// sdk/forgeops-feedback-dom/src/widget.ts
function esc(s) {
  return String(s != null ? s : "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}
function statusClass(display) {
  if (display === "\u5DF2\u5B8C\u6210") return "forgeops-st-done";
  if (display === "\u5F85\u9A8C\u8BC1") return "forgeops-st-verify";
  if (display === "\u9700\u8981\u8865\u5145") return "forgeops-st-info";
  if (display === "\u5F85\u5904\u7406") return "forgeops-st-todo";
  return "forgeops-st-doing";
}
function fmtTime(iso) {
  return iso ? iso.replace("T", " ").slice(0, 19) : "";
}
function mountWidget(container, core) {
  container.className = "forgeops-root";
  container.innerHTML = "";
  const state = {
    open: false,
    tab: "submit",
    detail: null,
    form: { type: "BUG", title: "", description: "", expectedBehavior: "", steps: "", note: "", reporterName: "" },
    submitting: false,
    error: "",
    successId: "",
    mineReporter: core.lastReporterName,
    mineList: [],
    mineError: "",
    verifyNote: "",
    verifyError: ""
  };
  const styleId = "forgeops-widget-style";
  if (!document.getElementById(styleId)) {
    const style = document.createElement("style");
    style.id = styleId;
    style.textContent = WIDGET_CSS;
    document.head.appendChild(style);
  }
  const failedCount = () => core.collector.failedRequests().length;
  const consoleCount = () => core.collector.consoleErrorList().length;
  function render() {
    if (!state.open) {
      container.innerHTML = `<button type="button" class="forgeops-fab" data-act="open">\u53CD\u9988</button>`;
      bindEvents();
      return;
    }
    container.innerHTML = `
      <div class="forgeops-panel">
        <div class="forgeops-header">
          <span class="forgeops-title">ForgeOps \u53CD\u9988</span>
          <button type="button" class="forgeops-close" data-act="close">\u2715</button>
        </div>
        <div class="forgeops-tabs">
          <button type="button" class="forgeops-tab ${state.tab === "submit" ? "active" : ""}" data-act="tab-submit">\u63D0\u4EA4\u53CD\u9988</button>
          <button type="button" class="forgeops-tab ${state.tab === "mine" ? "active" : ""}" data-act="tab-mine">\u6211\u7684\u53CD\u9988</button>
        </div>
        <div class="forgeops-body">${state.tab === "submit" ? renderSubmit() : renderMine()}</div>
      </div>`;
    bindEvents();
  }
  function renderSubmit() {
    const f = state.form;
    const preview = core.buildSubmission({ ...f });
    const { screenshot, ...rest } = preview;
    const autoJson = esc(JSON.stringify({ ...rest, screenshot: void 0 }, null, 2));
    return `
      <label class="forgeops-field">\u53CD\u9988\u7C7B\u578B
        <select data-field="type">
          <option value="BUG" ${f.type === "BUG" ? "selected" : ""}>Bug</option>
          <option value="OPTIMIZATION" ${f.type === "OPTIMIZATION" ? "selected" : ""}>\u4F18\u5316\u5EFA\u8BAE</option>
          <option value="REQUIREMENT" ${f.type === "REQUIREMENT" ? "selected" : ""}>\u9700\u6C42\u5EFA\u8BAE</option>
        </select>
      </label>
      <label class="forgeops-field">\u6982\u8981
        <input data-field="title" maxlength="120" placeholder="\u4E00\u53E5\u8BDD\u6982\u8981\uFF08\u53EF\u9009\uFF09" value="${esc(f.title)}">
      </label>
      <label class="forgeops-field">\u95EE\u9898\u63CF\u8FF0 <span class="forgeops-required">*</span>
        <textarea data-field="description" rows="3" maxlength="8000" placeholder="\u53D1\u751F\u4E86\u4EC0\u4E48\uFF1F">${esc(f.description)}</textarea>
      </label>
      <label class="forgeops-field">\u9884\u671F\u6548\u679C
        <textarea data-field="expectedBehavior" rows="2" maxlength="4000" placeholder="\u671F\u671B\u7684\u6B63\u786E\u884C\u4E3A">${esc(f.expectedBehavior)}</textarea>
      </label>
      <label class="forgeops-field">\u64CD\u4F5C\u6B65\u9AA4\uFF08\u53EF\u9009\uFF09
        <textarea data-field="steps" rows="2" maxlength="4000" placeholder="\u590D\u73B0\u6B65\u9AA4">${esc(f.steps)}</textarea>
      </label>
      <label class="forgeops-field">\u8865\u5145\u8BF4\u660E\uFF08\u53EF\u9009\uFF09
        <input data-field="note" maxlength="4000" value="${esc(f.note)}">
      </label>
      <label class="forgeops-field">\u4F60\u7684\u59D3\u540D <span class="forgeops-required">*</span>
        <input data-field="reporterName" maxlength="64" placeholder="\u7528\u4E8E\u9A8C\u8BC1\u95ED\u73AF\u901A\u77E5" value="${esc(f.reporterName)}">
      </label>
      <details>
        <summary class="forgeops-details-toggle">\u81EA\u52A8\u9644\u5E26\u4E0A\u4E0B\u6587\uFF08${failedCount()} \u6761\u5931\u8D25\u8BF7\u6C42 \xB7 ${consoleCount()} \u6761\u63A7\u5236\u53F0\u9519\u8BEF\uFF09</summary>
        <pre class="forgeops-pre">${autoJson}</pre>
      </details>
      ${state.error ? `<div class="forgeops-error">${esc(state.error)}</div>` : ""}
      ${state.successId ? `<div class="forgeops-success">\u5DF2\u63D0\u4EA4 ${esc(state.successId)}\uFF0C\u53EF\u5728\u300C\u6211\u7684\u53CD\u9988\u300D\u4E2D\u8DDF\u8E2A\u8FDB\u5EA6\u3002</div>` : ""}
      <button type="button" class="forgeops-submit" data-act="submit" ${state.submitting ? "disabled" : ""}>
        ${state.submitting ? "\u63D0\u4EA4\u4E2D\u2026" : "\u63D0\u4EA4\u53CD\u9988"}
      </button>`;
  }
  function renderMine() {
    if (state.detail) {
      const d = state.detail;
      return `
        <button type="button" class="forgeops-back" data-act="back">\u2190 \u8FD4\u56DE\u5217\u8868</button>
        <div class="forgeops-detail-title">${esc(d.id)} \xB7 ${esc(d.title)}</div>
        <div><span class="forgeops-status ${statusClass(d.displayStatus)}">${esc(d.displayStatus)}</span></div>
        ${d.deploymentVersion ? `<div class="forgeops-meta">\u90E8\u7F72\u7248\u672C\uFF1A<code>${esc(d.deploymentVersion)}</code></div>` : ""}
        ${d.prUrl ? `<div class="forgeops-meta">PR\uFF1A<a href="${esc(d.prUrl)}" target="_blank" rel="noreferrer">${esc(d.prUrl)}</a></div>` : ""}
        ${d.multicaIssueUrl ? `<div class="forgeops-meta">Issue\uFF1A<a href="${esc(d.multicaIssueUrl)}" target="_blank" rel="noreferrer">${esc(d.multicaIssueUrl)}</a></div>` : ""}
        <div class="forgeops-timeline">
          ${d.comments.map(
        (c) => `
            <div class="forgeops-comment">
              <div class="forgeops-comment-head"><span>${esc(c.author)}</span><span>${esc(fmtTime(c.createdAt))}</span></div>
              <div class="forgeops-comment-body">${esc(c.content)}</div>
            </div>`
      ).join("")}
        </div>
        ${d.displayStatus === "\u5F85\u9A8C\u8BC1" ? `
        <label class="forgeops-field">\u9A8C\u8BC1\u8BF4\u660E\uFF08\u53EF\u9009\uFF09
          <textarea data-field="verifyNote" rows="2" placeholder="\u9A8C\u8BC1\u8BF4\u660E">${esc(state.verifyNote)}</textarea>
        </label>
        <div class="forgeops-verify">
          <button type="button" class="forgeops-pass" data-act="verify-pass">\u9A8C\u8BC1\u901A\u8FC7</button>
          <button type="button" class="forgeops-fail" data-act="verify-fail">\u4ECD\u6709\u95EE\u9898</button>
        </div>` : ""}
        ${state.verifyError ? `<div class="forgeops-error">${esc(state.verifyError)}</div>` : ""}`;
    }
    return `
      <div class="forgeops-row">
        <input data-field="mineReporter" maxlength="64" placeholder="\u4F60\u7684\u59D3\u540D" value="${esc(state.mineReporter)}">
        <button type="button" data-act="load-mine">\u67E5\u8BE2</button>
      </div>
      ${state.mineError ? `<div class="forgeops-error">${esc(state.mineError)}</div>` : ""}
      ${state.mineList.map(
      (i) => `
        <div class="forgeops-item" data-act="open-detail" data-id="${esc(i.id)}">
          <div class="forgeops-item-line">
            <span class="forgeops-item-id">${esc(i.id)}</span>
            <span class="forgeops-status ${statusClass(i.displayStatus)}">${esc(i.displayStatus)}</span>
          </div>
          <div>${esc(i.title)}</div>
        </div>`
    ).join("")}
      ${!state.mineList.length && !state.mineError ? '<div class="forgeops-empty">\u6682\u65E0\u53CD\u9988\u8BB0\u5F55</div>' : ""}`;
  }
  function bindEvents() {
    container.querySelectorAll("[data-field]").forEach((el) => {
      const field = el.dataset.field;
      if (!field) return;
      const handler = () => {
        const value = el.value;
        if (field === "mineReporter") state.mineReporter = value;
        else if (field === "verifyNote") state.verifyNote = value;
        else if (field in state.form) state.form[field] = value;
        if (el.tagName === "SELECT") render();
      };
      el.addEventListener("change", handler);
      if (el.tagName === "INPUT" || el.tagName === "TEXTAREA") {
        el.addEventListener("input", handler);
      }
    });
    container.querySelectorAll("[data-act]").forEach((el) => {
      el.addEventListener("click", async (ev) => {
        var _a, _b, _c;
        const act = el.dataset.act;
        try {
          switch (act) {
            case "open":
              if (!state.form.reporterName) {
                const r = core.getReporter();
                state.form.reporterName = (_a = r == null ? void 0 : r.name) != null ? _a : state.form.reporterName;
                state.mineReporter = state.mineReporter || state.form.reporterName;
              }
              state.open = true;
              break;
            case "close":
              state.open = false;
              break;
            case "tab-submit":
              state.tab = "submit";
              break;
            case "tab-mine":
              state.tab = "mine";
              if (state.mineReporter) await loadMine();
              break;
            case "submit":
              await submit();
              break;
            case "load-mine":
              await loadMine();
              break;
            case "open-detail": {
              const id = (_c = (_b = el.closest("[data-id]")) == null ? void 0 : _b.dataset.id) != null ? _c : el.dataset.id;
              if (id) await openDetail(id);
              break;
            }
            case "back":
              state.detail = null;
              break;
            case "verify-pass":
              await verify(true);
              break;
            case "verify-fail":
              await verify(false);
              break;
          }
        } catch (e) {
          state.error = e instanceof Error ? e.message : String(e);
        }
        ev.stopPropagation();
        render();
      });
    });
  }
  async function submit() {
    const f = state.form;
    if (!f.description.trim() || !f.reporterName.trim()) {
      state.error = "\u8BF7\u586B\u5199\u95EE\u9898\u63CF\u8FF0\u4E0E\u4F60\u7684\u59D3\u540D";
      return;
    }
    state.error = "";
    state.successId = "";
    state.submitting = true;
    render();
    try {
      const payload = core.buildSubmission(f);
      const result = await core.client.submitFeedback(payload);
      state.successId = result.id;
      core.setReporterName(f.reporterName);
      state.mineReporter = f.reporterName;
      state.form.description = "";
      state.form.title = "";
      state.form.expectedBehavior = "";
      state.form.steps = "";
      state.form.note = "";
    } catch (e) {
      state.error = e instanceof Error ? e.message : String(e);
    } finally {
      state.submitting = false;
    }
  }
  async function loadMine() {
    if (!state.mineReporter.trim()) return;
    state.mineError = "";
    try {
      state.mineList = await core.client.listMyFeedback(state.mineReporter, core.options.projectId);
    } catch (e) {
      state.mineError = e instanceof Error ? e.message : String(e);
    }
  }
  async function openDetail(id) {
    state.verifyError = "";
    state.verifyNote = "";
    state.detail = await core.client.getFeedback(id);
  }
  async function verify(pass) {
    var _a;
    if (!state.detail) return;
    state.verifyError = "";
    const verifier = ((_a = core.getReporter()) == null ? void 0 : _a.name) || state.mineReporter || "unknown";
    try {
      if (pass) {
        await core.client.verifyPass(state.detail.id, verifier, state.verifyNote || void 0);
      } else {
        if (!state.verifyNote.trim()) {
          state.verifyError = "\u8BF7\u586B\u5199\u201C\u4ECD\u6709\u95EE\u9898\u201D\u7684\u5177\u4F53\u8BF4\u660E\uFF08\u9A8C\u8BC1\u8BF4\u660E\u5FC5\u586B\uFF09";
          return;
        }
        await core.client.verifyFail(state.detail.id, verifier, state.verifyNote, {
          requests: core.collector.failedRequests(),
          consoleErrors: core.collector.consoleErrorList()
        });
      }
      await openDetail(state.detail.id);
    } catch (e) {
      state.verifyError = e instanceof Error ? e.message : String(e);
    }
  }
  render();
  return {
    open() {
      state.open = true;
      render();
    },
    close() {
      state.open = false;
      render();
    },
    destroy() {
      container.innerHTML = "";
    }
  };
}

// sdk/forgeops-feedback-dom/src/index.ts
function initForgeOpsFeedback(options, target) {
  const core = new FeedbackCore(options);
  core.start();
  let widget = null;
  if (core.enabled) {
    const host = target != null ? target : document.body.appendChild(document.createElement("div"));
    widget = mountWidget(host, core);
  }
  return {
    core,
    widget,
    open: () => widget == null ? void 0 : widget.open(),
    close: () => widget == null ? void 0 : widget.close(),
    destroy: () => {
      widget == null ? void 0 : widget.destroy();
      widget = null;
    }
  };
}

// sdk/forgeops-feedback-react/src/index.tsx
import { jsx } from "react/jsx-runtime";
function ForgeOpsFeedback({ options, children }) {
  const hostRef = useRef(null);
  const coreRef = useRef(null);
  useEffect(() => {
    if (!hostRef.current) return;
    const core = new FeedbackCore(options);
    core.start();
    coreRef.current = core;
    const widget = core.enabled ? mountWidget(hostRef.current, core) : null;
    return () => {
      widget == null ? void 0 : widget.destroy();
      coreRef.current = null;
    };
  }, []);
  return /* @__PURE__ */ jsx("div", { ref: hostRef, children });
}
function useForgeOpsCore(options) {
  const ref = useRef(null);
  if (ref.current === null) {
    ref.current = new FeedbackCore(options);
  }
  useEffect(() => {
    var _a;
    (_a = ref.current) == null ? void 0 : _a.start();
  }, []);
  return ref.current;
}
export {
  FeedbackCore,
  ForgeOpsFeedback,
  ForgeOpsGatewayClient,
  RequestContextCollector,
  attachAxios,
  initForgeOpsFeedback,
  ulid,
  useForgeOpsCore
};
