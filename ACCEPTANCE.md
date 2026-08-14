# ForgeOps V0.1 实施验收清单

> 依据：`forgeops-architecture-v0.1.md` + `环境要求.md` + 2026-08-15 方案确认（全闭环 0+1+2+3 / Multica 升级最新 / monorepo 内 demo-app 试点 / SDK 内验证入口）。
> 每项验收均以**实际运行**为准，通过标准 = 操作/命令 + 期望结果全部命中。

## 0. 已确认的关键决定

| # | 决定 | 结论 |
|---|---|---|
| 1 | 实施范围 | Phase 0+1+2+3 全闭环；真实 CI/CD 用模拟回调替代 |
| 2 | Multica Server | 升级到上游最新（v0.4.26+），升级前 pg_dump 备份，本地 CLI 同步升级 |
| 3 | 试点仓库 | forgeops monorepo 内 `examples/demo-app`（预埋真实 Bug），Agent 对 wuxiy/forge-ops 建分支出 Draft PR |
| 4 | 验证闭环入口 | Feedback SDK 内「我的反馈」列表 + 详情 + [验证通过]/[仍有问题] |

## 1. 前置条件（需用户配合项）

| # | 项 | 说明 |
|---|---|---|
| P1 | GitHub PAT | 提供一个仅限 `wuxiy/forge-ops` 仓库、具备 contents:write + pull-requests:write 的 Token，注入 Coding Agent 环境变量（GITHUB_TOKEN），用于 push 分支与创建 Draft PR |
| P2 | 服务器操作窗口 | 允许在 172.16.65.59 上重建 multica 容器、新建 forgeops 数据库、追加 nginx 配置（均不触碰其他服务） |

## 2. 自行假设（非关键细节，按架构文档推导）

1. Gateway：Spring Boot 4 + Java 21 + Maven；复用 59 上 multica 的 pgvector/pg17 实例，新建独立数据库 `forgeops`（Flyway 管理 schema）；交付 docker-compose + nginx(:18090) 配置，开发期可本地运行。
2. SDK：Vue 3 Plugin，包名 `@forgeops/feedback-vue`（monorepo 内，发布名可后改）；axios 拦截器 + fetch patch 双通道请求缓冲（环形 50 条，白名单字段）；截图为可选能力（html2canvas，可配置关闭，失败不阻断）。
3. 链路：SDK 每请求生成 ULID `X-Request-ID` 并透传；demo-api 用 Filter + MDC 输出结构化 JSON 日志（requestId/traceId/userId/uri/method/status/version/commitSha），并提供按 requestId 查日志摘录的端点供 Gateway 拉取（替代真实 ES，日志查询接口抽象可替换）。
4. 反馈人身份：V0.1 不接 SSO；表单填写姓名必填，SDK 支持 `getCurrentUser` 注入宿主登录态。
5. 通知：V0.1 = SDK「我的反馈」状态 + 反馈时间线 + Multica Issue 评论；不做 IM/邮件。
6. 鉴权：内网测试，feedback API 不鉴权；callback API 用可配置共享 secret Header 校验。
7. Multica 工作区：新建 `engineering` workspace；两个 Agent：`forgeops-triage`、`forgeops-coding`；Runtime = 本地 Mac multica daemon（Claude Code）。
8. Agent 与 Gateway 之间状态同步：Gateway 轮询 Multica API（状态/评论）驱动状态机，不依赖 Multica 出站 webhook。
9. 用户可见状态固定为 §6.2 七态：待处理/AI 分析中/开发处理中/待发布/待验证/已完成/需要补充。

## 3. 验收清单

### A. Phase 0 — 基础打底（Multica / Runtime / Git）

| # | 验收项 | 验证方式 | 通过标准 |
|---|---|---|---|
| A1 | 升级前备份 | `ssh root@172.16.65.59` 执行 pg_dump 并落盘 | 备份文件存在且非空，记录路径 |
| A2 | Multica Server 升级 | `docker ps` + 访问 `http://172.16.65.59:13000` | 三个容器 healthy；UI 可登录；服务端版本 ≥ v0.4.26；原有账号可登录 |
| A3 | 本地 CLI 升级并连通 | `multica auth status` | Server 指向 18080，用户 wuxi，token 有效 |
| A4 | 本地 daemon 运行 | `multica daemon status` | Daemon: running，检测到 claude CLI |
| A5 | engineering workspace + 双 Agent | `multica agent list` | 存在 forgeops-triage、forgeops-coding 且运行时在线 |
| A6 | Git 打通 | 手动建 Multica Issue 指派 Coding Agent | Agent 在本地 checkout wuxiy/forge-ops → 新分支 → commit → push → GitHub 上可见 Draft PR |

### B. Phase 1 — 反馈入口 + Gateway + Context Pack

| # | 验收项 | 验证方式 | 通过标准 |
|---|---|---|---|
| B1 | demo-app 运行 | `pnpm dev`（web）+ `mvn spring-boot:run`（api） | 页面可访问；预埋 Bug 复现：空数据查询接口 500、页面持续 loading |
| B2 | SDK 采集 | 页面打开反馈面板并提交 | 表单含类型(Bug/优化建议/需求建议)+描述+预期+可选步骤；自动附带 URL/路由/标题/前后端版本与 Commit/用户/浏览器 OS/屏幕/最近失败请求/Console Error/RequestID；敏感字段（Authorization/Cookie/Body 等）不出现在 payload |
| B3 | Request-ID 链路 | 触发失败请求后查 api 日志 | 每条日志含 requestId（与 SDK 缓冲区一致）、结构化 JSON、含 version/commitSha |
| B4 | Feedback Intake | `POST /api/v1/feedback` | 返回 201 + `FB-xxxx`；DB forgeops_feedback/forgeops_context 各一条 |
| B5 | Context Pack V1 | 查 `forgeops_context.context_json` | 符合 schemas/context-pack.schema.json（schemaVersion 1.0），含 feedback/project/page/frontend/backend/requests/logs/git 字段；failed request 的日志摘录已自动补齐 |
| B6 | Project Registry | `GET /api/v1/projects/demo-app/config` | 返回 registry/projects/demo-app.yaml 解析结果（repo/branch/agents/policy 等） |
| B7 | 自动建 Multica Issue | 提交反馈后查 Multica | Issue 按 §13.1 模板生成（Feedback/Environment/Failed Requests/Context Pack/Acceptance Criteria），指派 forgeops-triage；feedback.multica_issue_id 回写；状态走到 TRIAGING |
| B8 | PII 双重防线 | 单元测试 + 构造含敏感内容提交 | Gateway Sanitizer 对姓名类/证件号/手机号模式做 Mask；SDK 端白名单外的字段不采集 |
| B9 | 回调鉴权与幂等表 | 任意 callback 请求 | forgeops_integration_event 记录 source/external_event_id/payload；错误 secret 被 401 拒绝 |

### C. Phase 2 — Agent 闭环（Triage → Coding → Draft PR）

| # | 验收项 | 验证方式 | 通过标准 |
|---|---|---|---|
| C1 | Triage Skill | 观察_multica Issue 评论 | 输出结构化分析：问题分类/影响范围/根因推断/证据(引用 requestId 日志)/相关文件/风险/是否进入 Coding；判定"进入 Coding" |
| C2 | 状态机转派 | 观察 Gateway 状态 | TRIAGING → CODING 自动发生（poller 驱动），Issue 重指派 forgeops-coding |
| C3 | Coding Agent 产 PR | 观察 GitHub | 新分支 `feature/agent/FB-xxxx`、含修复 commit、测试通过记录；Draft PR 描述含 §15 模板（问题/根因/修改内容/测试结果/风险/来源 FB+Multica ID） |
| C4 | 修复有效性 | 本地检出该分支运行 demo | 原复现场景变为：接口 200 + 空数据正常显示空态；原有有数据场景不受影响 |
| C5 | 状态与回写 | 查 feedback 记录 | pr_url 回写，状态 CODING → PR_REVIEW；SDK「我的反馈」显示"待 Review/待发布"映射态 |

### D. Phase 3 — CI / 验证闭环（模拟回调）

| # | 验收项 | 验证方式 | 通过标准 |
|---|---|---|---|
| D1 | Git 回调 | curl `POST /api/v1/callback/git`（PR merged） | 状态 PR_REVIEW → BUILDING；重复回调不重复处理（幂等） |
| D2 | CI 回调 | curl `POST /api/v1/callback/ci` | BUILDING → DEPLOYING |
| D3 | 部署回调+通知 | curl `POST /api/v1/callback/deployment`（SUCCESS + version） | 状态 → WAITING_VERIFY；反馈时间线出现待验证通知（含版本号）；SDK「我的反馈」状态变"待验证" |
| D4 | 验证通过 | SDK 详情页点[验证通过] | feedback.status=DONE；Multica Issue=Done；时间线留痕（verifier/时间） |
| D5 | 仍有问题→Reopen | 在详情页补充说明后点[仍有问题] | 原反馈 REOPENED（不新建 Issue）；新上下文追加为 Context Pack 快照；Multica 原 Issue Reopen + 新评论；Agent 被重新指派；状态回 TRIAGING/CODING |
| D6 | 用户可见状态映射 | 全流程走查 | 任意时刻 SDK 列表仅显示七态之一，与内部状态映射正确 |
| D7 | 审计留痕 | 查审计记录 | 提交/自动补数据/Agent 执行/PR/Review/CI/验证各环节可追溯 |

### E. 交付物与工程规范

| # | 验收项 | 通过标准 |
|---|---|---|
| E1 | Monorepo 结构符合 §21 | gateway/sdk/examples/registry/schemas/multica/skills/policy/deploy/docs 就位 |
| E2 | 部署物 | deploy/docker-compose 可在 59 一键起 Gateway；nginx 配置文件交付（:18090） |
| E3 | 接入文档 | docs/integration-guide.md：真实 Vue 项目接入 SDK ≤5 步；真实 Spring Boot 接入 Request-ID 链路说明；真实 CI 回调对接说明（含 payload 契约） |
| E4 | 测试 | Gateway 单测通过（sanitizer/状态机/幂等最低要求）；`mvn verify` 绿 |
| E5 | 安全底线 | policy/*.yaml 中 autoMerge=false 生效（Gateway 拒绝任何 agent merge 语义回调）；Agent 不持有 forge-ops 之外仓库凭据 |

## 4. 验收执行约定

1. 每项验收留证据：命令输出、DB 查询结果、Multica/GitHub 截图或 URL，汇总到本文件追加的「验收记录」章节。
2. 未通过项修复后重验，直至全部通过；无法在本环境验证的真实 CI 对接（E3 文档化）与生产发布除外。
3. Human Gate 不变：任何 Agent PR 的 merge 由人工完成（验收中由我以开发者身份模拟点击 merge）。
