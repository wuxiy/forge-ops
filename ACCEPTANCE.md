# ForgeOps V0.1 实施验收清单

> 依据：`forgeops-architecture-v0.1.md` + `环境要求.md` + 2026-08-15 方案确认（全闭环 0+1+2+3 / Multica 升级最新 / monorepo 内 demo-app 试点 / SDK 内验证入口）。
> 每项验收均以**实际运行**为准，通过标准 = 操作/命令 + 期望结果全部命中。

## ⏱ 验收记录（2026-08-15 实跑结果）

### A. Phase 0 — 基础打底

| # | 结果 | 证据 |
|---|---|---|
| A1 | ✅ | `multica-pre-upgrade-20260815-012839.sql.gz`（1.6M）留存于 59 `/root/backups/multica/` |
| A2 | ✅ | fork 合并上游 main（bbb8a5cd），容器重建 healthy；DB 迁移 186→277+ 自动执行；原有数据保留（cywu workspace、issue 均在）；UI :13000 可登录 |
| A3 | ✅ | 本地 CLI 0.4.26（brew 升级），`multica auth status` 指向 18080 用户 wuxi 有效 |
| A4 | ✅ | `multica daemon status`：running，检测到 claude 等 7 个 CLI，watch 2 workspace |
| A5 | ✅ | engineering workspace（ENG 前缀）+ forgeops-triage/forgeops-coding 双 Agent（claude runtime online）+ 对应 Skill 已挂载 |
| A6 | ✅* | Issue ENG-1 → Coding Agent 自主 SSH clone → 分支 `feature/agent/A6-TEST` → commit e88f910 → push 远端可见；**Draft PR 创建因无 gh/GitHub Token 走降级路径**（compare 链接），真 PR 待 P1 PAT |

### B. Phase 1 — 反馈入口 + Gateway + Context Pack

| # | 结果 | 证据 |
|---|---|---|
| B1 | ✅ | demo 页面查询 P-99999：接口 500（后端越界）+ 页面持续「加载中…」；P-10001 正常显示 3 条记录 |
| B2 | ✅ | SDK 面板自动上下文预览：URL/标题/前后端 version+commit/UA/屏幕 2560x1440/2 条失败请求含 requestId/console 错误；payload 无任何 Header/Body/凭据字段 |
| B3 | ✅ | demo-api ACCESS 结构化日志含 requestId（与 SDK 缓冲一致）、status、durationMs、version、commitSha、exception |
| B4 | ✅ | 浏览器真实提交 → 201 返回 `FB-1002` → forgeops_feedback/forgeops_context 各 1 行 |
| B5 | ✅ | context_json 符合 schema 1.0；failed request 的日志摘录自动补齐（含 ArrayIndexOutOfBoundsException 证据行） |
| B6 | ✅ | `GET /api/v1/projects/demo-app/config` 返回完整 YAML 解析结果（repo/branch/agents/policy） |
| B7 | ✅ | Multica Issue ENG-3 按 §13.1 模板自动创建并指派 triage agent；multica_issue_id/url 回写；状态 SUBMITTED→CONTEXT_BUILDING→TRIAGING |
| B8 | ✅ | 手机号 13812345678 在落库描述中呈现为 `***MASKED***`（Gateway Sanitizer）；SDK 端白名单预览可见；PiiSanitizerTest 5 用例通过 |
| B9 | ✅ | forgeops_integration_event 记录 6 条（两轮 git/ci/deployment）；错误 secret → 401 |

### C. Phase 2 — Agent 闭环

| # | 结果 | 证据 |
|---|---|---|
| C1 | ✅ | Triage 输出完整结构化报告：分类/影响/根因（前后端双缺陷精确定位 PatientController.java:39 与 App.vue catch 块）/证据（引用 requestId 日志）/相关文件/风险/建议方案/`TRIAGE_RESULT: PROCEED_CODING` |
| C2 | ✅ | Poller 自动 TRIAGING→CODING，ENG-3 重指派 forgeops-coding，agent 状态 working |
| C3 | ✅* | 分支 `feature/agent/FB-1002`（592f578）含修复 commit + 测试；评论含 §15 模板六段 + PR_URL + CODING_RESULT；`mvn test` 2/2 + `pnpm typecheck` 通过；**PR 为 PR_PENDING_MANUAL 降级路径**（同 A6 凭据缺口）；Reopen 二轮产出 ce2f851（弱网 loading 竞态修复） |
| C4 | ✅ | 检出分支实跑：P-99999 → 200 + total=0 + 空态正常显示；P-10001 → 200 + total=3 不受影响 |
| C5 | ✅ | pr_url 回写；CODING→PR_REVIEW；SDK「我的反馈」显示「待发布」 |

### D. Phase 3 — 验证闭环（模拟回调）

| # | 结果 | 证据 |
|---|---|---|
| D1 | ✅ | git 回调 → PR_REVIEW→BUILDING；重复 externalEventId → DUPLICATED；AGENT 冒充 merge → 拒绝（Human Gate） |
| D2 | ✅ | ci 回调 → DEPLOYING；pipelineId 回写 |
| D3 | ✅ | deployment 回调 → WAITING_VERIFY；版本 0.1.1-test/0.1.2-test 回写；SDK 列表「待验证」；时间线含待验证通知 |
| D4 | ✅ | SDK 点[验证通过]（附说明）→ DONE；Multica ENG-3=done；verification 记录 PASS |
| D5 | ✅ | SDK 点[仍有问题]（附说明）→ 原 FB-1002 REOPENED→TRIAGING（**未新建 Issue**）；forgeops_context 追加 REOPEN_APPEND 快照；ENG-3 重开+重指派；Agent 二轮修复产出新分支；全程禁止孤立 Bug |
| D6 | ✅ | 全流程实测经过：待处理→AI 分析中→开发处理中→待发布→待验证→已完成（六态）；「需要补充」态由 FeedbackStatusTest 映射单测覆盖 |
| D7 | ✅ | forgeops_audit_log 27 条：提交/Issue 创建/Triage/分支/人工 merge/CI/部署/Reopen/二轮/验证全链路可追溯 |

### E. 交付物与工程规范

| # | 结果 | 证据 |
|---|---|---|
| E1 | ✅ | monorepo 结构与 §21 一致（gateway/sdk/examples/registry/schemas/multica/policy/deploy/docs） |
| E2 | ✅ | `deploy/docker-compose/forgeops.yml` + `forgeops.env.example` + gateway Dockerfile + `deploy/nginx/forgeops-gateway.conf`（:18090）交付 |
| E3 | ✅ | docs/integration-guide.md：SDK 5 步接入 / Request-ID 链路 / 三回调契约（含 payload 示例） |
| E4 | ✅ | Gateway `mvn verify`：13 tests，BUILD SUCCESS（sanitizer/状态机/幂等/HumanGate/ContextPack） |
| E5 | ✅ | autoMerge/autoDeploy 硬编码 false（HumanGate）；AGENT merge 回调实测被拒；Agent 仅持 forge-ops 仓库访问（SSH key） |

### 唯一遗留：P1 GitHub PAT（A6/C3 的「GitHub 上可见 Draft PR」）

- Agent 的 clone/分支/commit/push 链路已两轮验证（592f578、ce2f851），并在 ENG-4 复验（`feature/agent/A6-PR`，cf6808f，ahead_by=1）。
- 缺口仅为最后一步：无 gh CLI / GitHub Token（FORGEOPS_GITHUB_TOKEN），Agent 无法调 GitHub API 建 PR，走 PR_PENDING_MANUAL 降级路径。
- 注意：早期报告中的 `dev...feature/agent/FB-1002` compare 链接已失效（该分支已在 D 阶段被人工 merge，ahead_by=0）。

**补齐方式（二选一，均已就绪）**：

1. **PAT 全自动（推荐，可完整重验 C3）**：提供 PAT（仅 wuxiy/forge-ops，contents:write + pull-requests:write）后执行：
   ```bash
   echo '{"FORGEOPS_GITHUB_TOKEN":"<pat>"}' | multica agent env set be80b548-e91e-4b81-8b94-ce4022d7dde8 --custom-env-stdin
   multica issue rerun ENG-4   # Agent 全自动创建 Draft PR
   ```
   预期 Issue 评论出现 `CODING_RESULT: PR_CREATED` + `PR_URL: https://github.com/wuxiy/forge-ops/pull/<n>`，随后复验本项。
2. **人工一键**：打开 https://github.com/wuxiy/forge-ops/compare/dev...feature/agent/A6-PR?expand=1 → 点「Create draft pull request」（需登录 GitHub）。

---

以下为原始验收清单（定义）。

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
