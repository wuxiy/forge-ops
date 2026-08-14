# ForgeOps V0.1 接入指南

本文说明三件事：① 真实 Vue 项目接入 Feedback SDK（5 步）；② 真实 Spring Boot 接入 Request-ID 链路；③ 真实 CI/CD 回调对接契约。

---

## 一、Vue 项目接入 Feedback SDK（≤5 步）

> 参考实现：`examples/demo-app/web`

1. **安装依赖**（monorepo 内 `workspace:*`，或发布后 `npm i @forgeops/feedback-vue`）
2. **注册插件**（main.ts）：
   ```ts
   import { createForgeOps } from '@forgeops/feedback-vue'

   const forgeops = createForgeOps({
     gatewayUrl: 'http://<gateway-host>:18090',   // ForgeOps Gateway
     projectId: '<project-id>',                  // 与 registry/projects/<id>.yaml 一致
     environment: 'test',                        // test/uat/staging，其他环境自动禁用
     getReporter: () => ({ id: user.id, name: user.name }),  // 接宿主登录态（可选）
     getFrontendInfo: () => ({ version: __APP_VERSION__, commit: __APP_COMMIT__ }),
     getBackendInfo: () => backendInfo,          // 启动时从 /api/version 拉取后注入（可选）
     screenshotEnabled: false,                   // true 需安装可选依赖 html2canvas
   })
   app.use(forgeops.plugin)
   ```
3. **挂载入口组件**：在根组件模板放 `<ForgeOpsWidget />`（右下角反馈入口 + 我的反馈）。
4. **axios 项目**（可选）：SDK 已全局 patch fetch；axios 项目另挂 `RequestContextCollector` 的拦截器（参考 SDK README）。
5. **提供后端版本接口** `/api/version` 返回 `{version, commit}`（见第二步）。

验证：测试环境打开页面 → 右下角出现「反馈」→ 提交后 Gateway 返回 `FB-xxxx` → 「我的反馈」可见状态流转。

**采集白名单**（只采集这些，其余一律不采集）：URL/Route/页面标题、前后端 version/commit、登录用户（宿主注入）、浏览器/OS/屏幕、最近失败请求的 method/url/status/耗时/requestId/traceId、console error、截图（可选）。Authorization/Cookie/请求体/响应体永不采集。

---

## 二、Spring Boot 接入 Request-ID 链路

> 参考实现：`examples/demo-app/api`（`RequestIdFilter.java`）

1. 复制 `RequestIdFilter`（或引 demo-api 模块）：读取/生成 `X-Request-ID`（ULID）→ MDC → 输出结构化 JSON 访问日志（requestId/traceId/userId/service/uri/httpMethod/status/durationMs/version/commitSha）。
2. 日志平台按 `requestId` 建索引（ES 场景在 Project Registry `observability.logs.type: elasticsearch` 配置查询）。
3. 暴露 `GET /api/version` 返回 `{service, version, commit}`（Maven build-info 自动生成）。
4. 反馈到达后：`requestId → 直接查到对应日志`（Context Pack 的 `logs` 段自动补齐）。

demo 使用的 `http-request-log` 类型提供 `GET /api/admin/requests/{requestId}` 内存日志端点；真实项目接 ES 时实现同语义查询即可，Context Pack 结构不变。

---

## 三、CI/CD 回调对接（现有流水线加 3 个 curl）

**前置**：PR 描述含 `Feedback: FB-xxxx`（Coding Agent 自动生成）。回调需要 Header `X-ForgeOps-Callback-Secret: <secret>`。

1. **Git 回调（PR merged 后触发）**
   ```bash
   curl -X POST http://<gateway>/api/v1/callback/git \
     -H 'Content-Type: application/json' \
     -H 'X-ForgeOps-Callback-Secret: <secret>' \
     -d '{"projectId":"<id>","feedbackId":"FB-1023","externalEventId":"<git-event-uuid>",
          "eventType":"PR_MERGED","actor":"<merge-user>","actorType":"USER",
          "prUrl":"<pr-url>","commitSha":"<sha>","status":"MERGED"}'
   ```
2. **CI 回调（构建结束）**：`POST /api/v1/callback/ci`，`{"feedbackId":"FB-1023","externalEventId":"<build-id>","pipelineId":"12831","status":"SUCCESS"}`
3. **部署回调（发布测试环境成功）**：`POST /api/v1/callback/deployment`，`{"feedbackId":"FB-1023","externalEventId":"<deploy-id>","environment":"test","version":"1.8.4","commitSha":"<sha>","status":"SUCCESS"}`

幂等：`externalEventId` 重复投递自动忽略；状态机非法迁移忽略（例如 CI 早于 merge 到达）。
状态流：`PR_REVIEW → BUILDING → DEPLOYING → WAITING_VERIFY`，部署成功后原反馈人自动收到待验证通知（SDK「我的反馈」变「待验证」）。

---

## 四、新项目接入清单

1. `registry/projects/<id>.yaml` 一份（repo/branch/agents/policy/observability）
2. `POST /api/v1/projects/reload` 或重启 Gateway
3. 业务前端接入 SDK（上面第一节）
4. 业务后端接入 Request-ID 链路（上面第二节）
5. （可选）Multica 建对应 Triage/Coding Agent 并写入 yaml

平台核心代码零改动。
