# ForgeOps V0.1 接入指南

本文说明三件事：① 前端项目接入 Feedback SDK（Vue2 / Vue3 / React，均为「加依赖 + 几行配置」）；② Spring Boot 接入 Request-ID 链路（Starter 一行依赖）；③ 真实 CI/CD 回调对接契约。

> 前端架构：`@forgeops/feedback-core`（框架无关采集/客户端）+ `@forgeops/feedback-dom`（唯一 UI 实现）+ 三个框架薄壳。三栈行为完全一致。

---

## 一、前端接入（Vue2 / Vue3 / React）

### Vue 3（参考：examples/demo-app/web）

```bash
pnpm add @forgeops/feedback-vue
```
```ts
// main.ts
import { createForgeOps } from '@forgeops/feedback-vue'

const forgeops = createForgeOps({
  gatewayUrl: 'http://<gateway-host>:18092',   // ForgeOps Gateway
  projectId: '<project-id>',                  // 与 registry/projects/<id>.yaml 一致
  environment: 'test',                        // test/uat/staging，其他环境自动禁用
  getReporter: () => ({ id: user.id, name: user.name }),  // 接宿主登录态（可选，否则表单填写）
  getFrontendInfo: () => ({ version: __APP_VERSION__, commit: __APP_COMMIT__ }),
  getBackendInfo: () => backendInfo,          // 启动时从 /api/version 拉取后注入（可选）
})
app.use(forgeops.plugin)   // 右下角自动出现「反馈」入口；旧版 <ForgeOpsWidget /> 兼容无需移除
```

### Vue 2（参考：examples/demo-app/vue2-demo）

```bash
pnpm add @forgeops/feedback-vue2
```
```js
// main.js
import { createForgeOps } from '@forgeops/feedback-vue2'

Vue.use(createForgeOps({ gatewayUrl, projectId, environment: 'test' }))  // 入口自动挂载
```

### React（参考：examples/demo-app/react-demo）

```bash
pnpm add @forgeops/feedback-react
```
```tsx
// main.tsx（方式一：一行全局初始化，推荐）
initForgeOpsFeedback({ gatewayUrl, projectId, environment: 'test' })

// 方式二：组件挂载（随组件生命周期创建/销毁）
<ForgeOpsFeedback options={options}><App /></ForgeOpsFeedback>
```

### axios 项目（可选）

SDK 已全局 patch fetch；axios 请求另挂拦截器（core 提供，无 axios 依赖）：

```ts
import { attachAxios, useForgeOpsCollector } from '@forgeops/feedback-<vue|vue2|react>'
// 任意位置拿到 collector（如 vue3 经 provide/context；或自行 new RequestContextCollector）
attachAxios(axiosInstance, collector)
```

验证：测试环境打开页面 → 右下角出现「反馈」→ 提交后返回 `FB-xxxx` → 「我的反馈」可见状态流转与验证入口。三栈实测记录：FB-1003（Vue2）、FB-1004（React）、FB-1002（Vue3 全闭环）。

**采集白名单**（只采集这些，其余一律不采集）：URL/Route/页面标题、前后端 version/commit、登录用户（宿主注入）、浏览器/OS/屏幕、最近失败请求的 method/url/status/耗时/requestId/traceId、console error。Authorization/Cookie/请求体/响应体永不采集。

---

## 二、Spring Boot 接入 Request-ID 链路（Starter 方式）

> 参考实现：`examples/demo-app/api`（本身即依赖本 Starter 运行）

**一步引入**（既有 / 新增项目相同）：

1. 加依赖（本地构建：`cd sdk/forgeops-spring-boot-starter && mvn install`）：
   ```xml
   <dependency>
     <groupId>com.forgeops</groupId>
     <artifactId>forgeops-spring-boot-starter</artifactId>
     <version>0.1.0-SNAPSHOT</version>
   </dependency>
   ```
2. 可选配置（默认已可用，通常零配置）：
   ```yaml
   forgeops:
     tracing:
       enabled: true              # 引入依赖即默认开启；false 可整体停用
       version-endpoint: true     # GET /api/version（version/commit）
       expose-request-logs: true  # GET /api/admin/requests/{requestId}
       tracked-requests: 500      # 内存请求日志缓冲量
   ```

引入后自动获得（Spring Boot 3.x / 4.x 均可，编译基线 Boot 3.4 + Java 17+）：

- **Request-ID 链路**：读取/生成 `X-Request-ID`（ULID，与前端 SDK 一致）→ MDC → 响应头回显
- **结构化 JSON 访问日志**（logger `ACCESS`）：requestId/traceId/service/uri/httpMethod/status/durationMs/version/commitSha/exception
- **`GET /api/version`**：返回 `{service, version, commit}`（读取应用 Maven build-info，`spring-boot-maven-plugin` 配 `build-info` goal 即含 commit）
- **`GET /api/admin/requests/{requestId}`**：内存请求日志查询（ForgeOps Gateway Context Pack 的 `logs` 段即按此拉取）

真实项目接 ES 时，把 Project Registry 的 `observability.logs.type` 配为 `elasticsearch` 并实现同语义查询即可，Context Pack 结构不变。

> 零依赖说明：Starter 不绑定 Jackson（手工 JSON 输出 + 端点返回 Map 由宿主序列化），因此同时兼容 Jackson 2（Boot 3）与 Jackson 3（Boot 4）。Boot 2（javax.servlet）项目请沿用手工方式（复制 `sdk/forgeops-spring-boot-starter` 中 `RequestIdFilter`/`RequestLogStore`，包名换 javax）。

## 二·B、Node.js（Fastify 5）接入 Request-ID 链路

> 参考实现：akso-agent-databot（`server/src/forgeops/tracing.ts` + index.ts 三行注册）

零依赖单文件插件（源头 `sdk/forgeops-tracing-fastify`），复制到目标项目即可：

```ts
// server/src/index.ts —— 注意用「直接调用」而非 app.register：
// Fastify 插件默认封装（encapsulation），register 形式 hooks 只作用于插件 scope，
// 无法覆盖业务路由；setupForgeopsTracing 直接调用才能全量生效。
import { setupForgeopsTracing } from './forgeops/tracing.js'

await setupForgeopsTracing(app, {
  serviceName: 'your-service',       // 日志与 /api/version 中的服务名
  version: '1.0.0', commit: 'xxx',   // 可选，默认读 APP_VERSION/APP_COMMIT env
  logger,                            // 可选，复用宿主 pino；缺省 console
})
```

自动获得与 Spring Boot Starter 等价能力：X-Request-ID 透传/回显（ULID）、结构化访问日志（含 exception）、`GET /api/version`、`GET /api/admin/requests/{requestId}`。实测：akso FB-1005 的 Context Pack 自动补齐 10 条 Fastify 日志摘录。

**前端跨仓库分发**（目标项目不在本 monorepo）：`node scripts/build-integration-bundle.mjs` 生成自包含单文件（`forgeops-feedback.mjs` + `.d.ts`，react 外部化），复制进目标项目 `src/forgeops/` 后：

```tsx
import { initForgeOpsFeedback } from './forgeops/forgeops-feedback.mjs'
initForgeOpsFeedback({ gatewayUrl, projectId, environment: 'test' })
```

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

1. `registry/projects/<id>.yaml` 一份（repo/branch/agents/policy/observability/**feedback.prefix**）

**反馈编号规则**：registry 配置 `feedback.prefix`（项目短码，如 akso-agent-databot→`ADB`）后，该项目反馈编号为 `ADB-FB-1001` 起独立递增；未配置则沿用全局 `FB-n`。multica 侧 workspace/project 也在此 YAML 配置（`multica.workspace`/`multica.project` 按名称自动解析）。Agent 产出的 MR 标题模板：`fix(<反馈标识> / <multica Issue标识>): <摘要>`（如 `fix(ADB-FB-1001 / AKSO-2): ...`）——multica 靠 Issue 标识自动关联 MR。
2. `POST /api/v1/projects/reload` 或重启 Gateway
3. 业务前端接入 SDK（上面第一节）
4. 业务后端接入 Request-ID 链路（上面第二节）
5. （可选）Multica 建对应 Triage/Coding Agent 并写入 yaml

平台核心代码零改动。
