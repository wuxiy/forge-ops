# ForgeOps 2.0 实施计划

> 状态：实施基线，待 Owner 按阶段执行
>
> 日期：2026-09-12
>
> 配套验收清单：[`forgeops-2.0-acceptance-checklist.md`](./forgeops-2.0-acceptance-checklist.md)
>
> 历史材料：`forgeops-architecture-v0.1.md` 与 `ACCEPTANCE.md` 只作为 V0.1 证据，不作为 2.0 实现约束。

## 1. 结论与实施边界

ForgeOps 2.0 不是通用 Agent 管理平台，也不是 Personal Workstation。它只验证一个假设：

> 在真实项目中，ForgeOps 能否把自然产生的反馈可靠地转化为受控 Agent 执行、可复核 Draft PR 和可回溯验证闭环，并显著减少人工准备上下文与跟踪状态的时间。

2.0 采用以下不可变决议：

1. **ForgeOps 拥有工作流。** Feedback、Work Cycle、状态迁移、重试、CI/CD、验证和审计的唯一事实源是 ForgeOps 数据库。
2. **Paseo 只执行 Agent Run。** 2.0 主链使用 Paseo；不把 Paseo Hub Workflow 作为 ForgeOps 状态机。
3. **Multica 不进入同一条主链。** Multica 继续用于人直接创建、分派和观察 Agent 工作；2.0 不再通过 Issue 评论标记驱动 ForgeOps 状态。
4. **不做运行时插件平台。** 2.0 只有一个 Paseo 实现。`AgentExecution` 是供 ForgeOps 调用的深模块接口，不建设 Provider 注册中心；出现第二个真实实现需求后再抽取 Adapter seam。
5. **不兼容 V0.1 数据和接口。** 新建 `forgeops_v2` 数据库，重建迁移、状态和接口；不做数据迁移、双写、旧状态映射或旧 Multica Issue 续接。
6. **Agent 自动化终点仍为 Draft PR。** Merge、生产部署和最终验收只能由授权用户完成。
7. **实验也必须可靠和可审计。** “可以重试”“不会串项目”“不会泄露上下文”“不会错误显示完成”是实验前提，不是生产化增项。

## 2. 2.0 成功标准

技术闭环通过只说明系统可运行；是否继续投入由真实样本决定。

| 维度 | Go 标准 |
|---|---|
| 样本 | 至少 2 个真实仓库、20～30 条自然产生的反馈；预埋 Bug 不计入价值样本 |
| Agent 交付 | 至少 60% 的有效 Coding 样本无需人工重新组织 Prompt 即产生可 Review Draft PR |
| 人工效率 | “收到反馈到具备可执行上下文”的人工中位耗时较基线下降至少 50% |
| 可靠性 | 无重复 Run、错误复用旧结果、永久卡死或错误完成；注入故障后均可自动恢复或明确转人工 |
| 安全 | 0 次越权写入、跨项目访问、凭证泄露和已知敏感数据外发 |
| Human Gate | 0 次 Agent Merge、生产部署或伪造人工操作成功 |
| 使用行为 | 至少 70% 的有效 Coding 样本无需绕过 ForgeOps 重新手工准备同一份上下文 |

若样本量不足、使用频率低、人工时间没有下降，或维护运行环境的成本高于节省时间，则停止平台化，只保留 Feedback SDK、Context Pack 或必要的轻量 Glue Code。

指标口径：

- **自然反馈**：在真实工作中自然产生，不是为演示或凑样本预先植入的问题；
- **有效 Coding 样本**：Triage 判断需要改代码，且人工复核同意该分类；
- **可 Review Draft PR**：目标仓库、分支和 Commit 正确，修改覆盖验收条件，无明显越界，并提供真实测试证据；
- **人工重新组织 Prompt**：人工重新收集或改写 ForgeOps 已应提供的上下文后，直接向 Agent 发起同一任务；只做 Review 意见不计入；
- **上下文准备时间**：从收到反馈到人工认为信息足以开始定位的主动操作时间，不含排队等待。

## 3. 明确不做

2.0 不包含：

- Personal Workstation 的 Focus、Priority、Decision、Inbox、Calendar 或跨生活领域调度；
- Keep、Sentry、HolmesGPT、自动巡检、通用 SRE RCA；
- 新增前端框架、移动端、通用管理后台或视觉重构；
- 继续维护 Vue2/Vue3/React 三套 SDK 兼容面；2.0 只保留 framework-neutral Core/DOM 接口；
- Agent Squad、模型路由、多个 Runtime Provider、自动选择 Agent；
- 自动 Merge、自动生产部署、自动最终验收；
- V0.1 数据迁移、旧接口兼容、旧 Multica Issue 同步；
- 为尚不存在的第三种日志、VCS、CI 或 Agent Provider 提前建设插件体系；
- 以模拟回调、启动成功或测试进程退出码替代真实闭环验收。

## 4. 目标架构

```text
Feedback SDK / Host Identity
            │
            ▼
┌─────────────────────────────┐
│ ForgeOps Gateway            │
│                             │
│ AccessControl               │
│ FeedbackWorkflow            │
│ ContextPreparation          │
│ ProjectCatalog              │
│ IntegrationInbox / Outbox   │
│ DeliveryEvidence            │
│ AgentExecution              │
└──────────────┬──────────────┘
               │ internal authenticated interface
               ▼
┌─────────────────────────────┐
│ forgeops-paseo-runtime      │
│ TypeScript + @getpaseo/client │
│ submit / inspect / cancel   │
└──────────────┬──────────────┘
               │
               ▼
        Paseo daemon + Codex
               │
               ▼
       isolated worktree / Draft PR

Git/VCS Webhook ──► IntegrationInbox ──► DeliveryEvidence
CI Webhook      ──► IntegrationInbox ──► FeedbackWorkflow
Deploy Webhook  ──► IntegrationInbox ──► FeedbackWorkflow
```

### 4.1 模块与接口

| 模块 | 对调用方公开的接口 | 必须隐藏的实现复杂度 |
|---|---|---|
| `ProjectCatalog` | `resolve(projectId)` | YAML 读取、Schema 校验、全局策略合并、路径白名单、原子刷新 |
| `ContextPreparation` | `prepare(feedbackId, cycleId)` | 请求/日志关联、字段白名单、PII 清理、截图策略、Schema 校验、快照落库 |
| `FeedbackWorkflow` | `submit(command)`、`snapshot(feedbackId)` | 状态迁移、乐观锁、Cycle、失败分类、领域事件和审计 |
| `AgentExecution` | `submit(request)`、`inspect(runId)`、`cancel(runId, reason)` | Paseo SDK、连接恢复、结构化输出、超时、幂等、运行目录和权限 |
| `IntegrationInbox` | `accept(source, externalEventId, payload)`、`reconcile()` | 签名校验、去重、乱序暂存、重放、拒绝原因和死信 |
| `DeliveryEvidence` | `verifyPr(ref)`、`verifyBuild(ref)`、`verifyDeployment(ref)` | Git/VCS/CI/部署查询、repo/branch/commit 绑定、结果防伪 |
| `AccessControl` | `authenticate(credential)`、`authorize(principal, action, resource)` | 短期令牌、项目 Scope、回调签名、CORS、审计和拒绝响应 |

接口不得暴露 Paseo Agent ID、Multica Issue 评论格式、具体 CLI 参数等 Provider 细节。测试从上述接口进入，不绕过接口验证内部类。

## 5. 数据模型与状态模型

### 5.1 全新数据库

使用新的 `forgeops_v2` 数据库和从 `V1` 开始的迁移。V0.1 数据如需研究可先导出快照，但不导入 2.0。

核心表：

| 表 | 用途与关键约束 |
|---|---|
| `feedback` | 反馈稳定身份、项目、创建人、当前 Cycle、用户可见状态、`version` 乐观锁 |
| `feedback_cycle` | 每次初始处理或 Reopen 一个不可复用的 Cycle；`(feedback_id, cycle_no)` 唯一 |
| `context_snapshot` | 每个 Cycle 的不可变脱敏上下文；保存 Schema 版本、内容摘要和脱敏统计 |
| `agent_run` | 每次 Triage/Coding 尝试；保存 `cycle_id`、role、attempt、provider run ID、结果和失败分类 |
| `integration_event` | 外部事件 Inbox；`(source, external_event_id)` 唯一；区分 RECEIVED/APPLIED/DEFERRED/REJECTED |
| `outbox_event` | 与领域状态同事务写入；提交后异步调用外部系统；记录重试、下次执行和最终错误 |
| `verification_record` | PASS/REOPEN 及授权验证人；REOPEN 必须引用新 Cycle |
| `audit_log` | 追加写审计；记录 actor、action、resource、cycle/run、结果和 trace ID |
| `project_feedback_counter` | 项目内编号；并发安全、无重复 |

### 5.2 Cycle 规则

1. 初次提交创建 Cycle 1。
2. Reopen 创建 Cycle N+1；旧 Cycle、Context Snapshot 和 Agent Run 永远不修改、不重新消费。
3. 每个 Cycle 最多有一个当前 Triage Run 和一个当前 Coding Run；重试增加 `attempt`，不覆盖旧 Run。
4. 所有外部相关事件必须携带或解析出 `feedbackId + cycleId/runId + projectId`；不能只凭评论文本或 PR URL 推断。
5. Feedback 的 `current_cycle_id` 与状态变更采用乐观锁；并发命令最多一个成功。

### 5.3 内部状态

```text
RECEIVED
  → CONTEXT_READY
  → TRIAGE_QUEUED → TRIAGE_RUNNING
      ├─ NEEDS_INPUT
      ├─ NO_CODE_REQUIRED
      ├─ TRIAGE_FAILED
      └─ CODE_QUEUED → CODE_RUNNING
            ├─ EXECUTION_FAILED
            └─ PR_READY
                  → BUILD_RUNNING
                      ├─ BUILD_FAILED
                      └─ DEPLOY_RUNNING
                            ├─ DEPLOY_FAILED
                            └─ WAITING_VERIFY
                                  ├─ DONE
                                  └─ REOPENED → 新 Cycle CONTEXT_READY
```

状态要求：

- `BUILD_FAILED` 与 `DEPLOY_FAILED` 分离，并允许对应阶段显式重试；
- 非法迁移返回冲突，不得静默忽略；
- 终态不可被外部事件倒退；
- `FAILED` 状态必须包含可机器判断的 failure category、是否可重试和最后错误；
- 用户可见状态由内部状态纯映射生成，不单独写入第二份可漂移状态；
- 乱序事件进入 `DEFERRED`，前置条件满足后重放，不标成成功处理。

## 6. 核心契约

### 6.1 AgentExecution

概念接口：

```text
submit(ExecutionRequest) -> ExecutionHandle
inspect(ExecutionRunId)   -> ExecutionSnapshot
cancel(ExecutionRunId, reason) -> ExecutionSnapshot
```

`ExecutionRequest` 只包含 ForgeOps 语义：

- `idempotencyKey`：`feedbackId/cycleId/role/attempt`；
- `projectId`、受信任的 `workspaceRef`、`baseBranch`；
- `role`：`TRIAGE` 或 `CODING`；
- `contextSnapshotId` 与经脱敏的 prompt；
- `outputSchemaRef`；
- `timeout`、权限策略引用和关联 Trace ID。

Paseo model、mode、CLI、daemon URL 和 provider-specific 参数由实现内部配置，不能由浏览器请求传入。

### 6.2 结构化 Agent 结果

Triage 结果至少包含：

- `decision`: `NEEDS_INPUT | NO_CODE_REQUIRED | PROCEED_CODING`；
- `summary`、`rootCause`、`evidence[]`、`relatedFiles[]`；
- `missingInformation[]`、`risks[]`、`suggestedPlan[]`。

Coding 结果至少包含：

- `outcome`: `PR_CREATED | NO_CHANGE | FAILED`；
- `branch`、`commitSha`、`prUrl`；
- `changedFiles[]`、`tests[]`、`risks[]`；
- `failureCategory` 与 `failureMessage`。

任何不符合 Schema 的输出均为 `INVALID_OUTPUT`，不得通过字符串包含判断推进状态。Agent 报告的 PR、Commit、测试结果只作为声明，必须由 `DeliveryEvidence` 独立查询验证。

### 6.3 外部事件

所有 Git、CI、Deployment 事件必须包含：

- `externalEventId`，缺失直接拒绝；
- `source`、`projectId`、`repository`；
- 可关联的 `feedbackId`、`cycleId` 或 `runId`；
- `commitSha`，CI 与部署还必须包含目标环境；
- Provider 原始签名和接收时间。

共享 Secret 只用于不支持签名的内部实验适配器；GitHub/GitLab 等优先验证官方 Webhook 签名。不得信任调用方提交的 `actorType=USER` 作为人工身份依据。

## 7. 分阶段实施

阶段严格按依赖顺序执行。每个阶段只有在配套验收项全部通过后才能进入下一阶段；环境阻塞或跳过不算通过。

### Phase 0：冻结范围与建立基线

实施内容：

1. 标记 V0.1 架构和验收为历史材料，不继续在 V0.1 状态机上加功能。
2. 记录当前 commit、可执行测试、跳过项和一条 V0.1 演示链路。
3. 建立 `docs/evidence/v2.0/<验收ID>/` 证据约定。
4. 固定两个试点仓库候选，但此阶段不预埋新的价值样本 Bug。
5. 明确 Paseo daemon、Codex、Git/VCS 和测试环境的实际运行主机及凭证 Scope。
6. 在隔离的临时 Worktree 做 Paseo 技术探针，实测 SDK 的 submit/inspect/cancel、结构化输出、超时和 daemon 断线；记录并锁定 SDK、daemon 与 Codex 版本。
7. 冻结指标口径和记录模板，并开始收集至少 5 条可比的手工处理基线；如果没有可信历史耗时，则在同一试点期保留手工对照样本。

完成标准：验收 `S-*` 全部通过。若 Paseo 技术探针无法满足关键契约，立即停止并重新评审 Runtime 选择，不进入数据库重写。

### Phase 1：重建领域状态与数据库

实施内容：

1. 新建 `forgeops_v2` 数据库；清空并重写 Flyway 迁移历史。
2. 引入 Feedback/Cycle/Context Snapshot/Agent Run/Inbox/Outbox/Audit 模型。
3. 将状态迁移集中在 `FeedbackWorkflow`；Controller、Poller、Callback 均不能直接修改状态。
4. 为每一条允许和拒绝的迁移建立参数化测试。
5. Reopen 改为创建新 Cycle，删除复用旧评论结果的路径。
6. 使用乐观锁处理并发提交、回调、重试与验证。

建议位置：

```text
gateway/.../feedback/domain/
gateway/.../workflow/
gateway/.../integration/
gateway/.../audit/
gateway/.../resources/db/migration/
```

完成标准：验收 `WF-*` 全部通过。

### Phase 2：可靠 Inbox、Outbox 与对账

实施内容：

1. 外部调用全部移出数据库事务；同事务只写领域状态和 Outbox。
2. Outbox Dispatcher 支持指数退避、最大尝试次数、可观察失败和人工重放。
3. Inbox 先持久化再应用；重复事件返回相同结果，乱序事件进入 DEFERRED。
4. 实现 Reconciler：扫描超时 Run、未发送 Outbox、DEFERRED Inbox 和状态不一致项。
5. 对“外部成功但响应丢失”“数据库提交失败”“进程在关键点退出”做故障注入。

完成标准：验收 `REL-*` 全部通过。

### Phase 3：身份、权限与 Context 安全

实施内容：

1. 浏览器 SDK 使用宿主后端签发的短期身份令牌；令牌包含 subject、projectId、scope、expiry。
2. `mine`、详情、评论、验证和 Reopen 均按受信任 subject 授权，不能使用请求体姓名决定权限。
3. CORS 改为项目级 Allowlist；默认无匹配即拒绝。
4. 建立单一 `ContextPreparation` 清理链，对初次反馈、日志、Reopen、页面字段和 Agent Prompt 全量处理。
5. 截图默认关闭；启用时必须由用户预览确认，并记录是否发送，不记录原图内容到日志。
6. 凭证使用每仓库 Deploy Key 或细粒度 Token；Agent 无生产环境和其他仓库权限。
7. Agent 输出和用户文本均视为不可信数据，禁止其改变系统权限、状态机或 Human Gate。

完成标准：验收 `SEC-*` 全部通过。

### Phase 4：Project Registry 与单一 SDK 生命周期

实施内容：

1. 为 Project Registry 定义并执行 Schema；启动时任一启用项目无效即失败。
2. 配置刷新采用“完整解析校验成功后一次替换”，不能出现空窗口或部分加载。
3. 全局策略与项目策略解析为不可变 `ResolvedProject`；项目只能收紧权限。
4. 2.0 只发布 framework-neutral Core/DOM 接口；试点应用直接调用同一个初始化入口。
5. `start/stop/destroy` 必须幂等，并完整恢复 fetch、console.error、拦截器和 DOM。
6. 旧 Vue2/Vue3/React 薄壳从 2.0 workspace/发布物移除或明确归档，不承担兼容义务。
7. 在两个实际试点应用分别执行类型检查和浏览器旅程，验证框架无关接口可以接入。

完成标准：验收 `EDGE-*` 全部通过。

### Phase 5：接入 Paseo 单次执行

实施内容：

1. 新建 `runtime/forgeops-paseo-runtime`，使用 TypeScript 和官方 `@getpaseo/client`。
2. 只提供 submit/inspect/cancel 三个内部操作；接口绑定私网或 loopback，并使用 Gateway 到 Runtime 的服务凭证。
3. Runtime 对 `idempotencyKey` 做持久幂等，确保响应丢失重试不会创建第二个 Agent Run。
4. Triage 与 Coding 使用独立结构化输出 Schema；无效输出不推进工作流。
5. 每个 Coding Run 使用独立 Worktree；路径只能从 `ProjectCatalog` 的 Allowlist 解析。
6. 配置超时、取消、daemon 离线恢复、最大并发和每项目并发。
7. 强制禁止 Agent Merge、生产部署、读取非项目路径和使用全局 Git 凭证。
8. 删除 `MulticaPoller`、评论标记解析及 ForgeOps 主链中的 Multica Issue 字段。
9. 不引入 Paseo Hub Workflow；状态与重试仍由 ForgeOps 控制。

完成标准：验收 `AGT-*` 全部通过。

### Phase 6：真实 Git、CI、部署证据链

实施内容：

1. 接入一个真实 Git Provider Webhook，验证签名并保存原始事件摘要。
2. `DeliveryEvidence` 独立查询 PR，核对 repo、base、branch、commit、draft 和当前 Cycle。
3. 只有授权用户在 Provider 完成 Merge 后才进入 BUILD_RUNNING。
4. 接入真实 CI 成功/失败事件，并与 PR head commit 精确绑定。
5. 接入真实测试环境部署成功/失败事件，并与同一 commit、环境和版本绑定。
6. 支持 CI/部署事件重复、乱序和失败后重试。
7. 验证通过进入 DONE；仍有问题创建新 Cycle 并重新生成 Context Snapshot。

模拟回调只能用于单元和契约测试，不能签收本阶段。

完成标准：验收 `DEL-*` 全部通过。

### Phase 7：部署、可观测与恢复

实施内容：

1. 修复 Compose Registry 挂载、服务地址、持久卷和健康检查。
2. Gateway、PostgreSQL、Paseo Runtime/daemon 使用明确服务名；容器内不得用 `127.0.0.1` 指向其他服务。
3. 默认仅暴露给 LAN/Tailscale；不允许使用默认 Secret 启动。
4. 建立 Metrics：状态停留、Outbox 重试、Deferred 事件、Run 成功率、恢复次数、人工介入次数。
5. 建立结构化日志和 Trace ID，但不记录 Prompt、Token、Cookie、原始 Context 或截图。
6. 编写并实跑 `verify-v2-e2e.sh`、`verify-v2-recovery.sh` 和 `verify-v2-security.sh`。
7. 验证 Gateway、Runtime、Paseo daemon 和数据库分别重启时的恢复行为。

完成标准：验收 `OPS-*` 全部通过；`Q-*` 作为跨阶段质量 Gate 持续执行，最迟在最终决策前全部通过。

### Phase 8：真实样本试点

实施内容：

1. 选择两个结构不同的真实仓库；至少一个不是当前 demo-app 的同构复制。
2. 使用 Phase 0 冻结的口径和基线：准备上下文时间、人工 Prompt 次数、完成时间、Review 时间。
3. 连续收集 20～30 条自然反馈，禁止为凑数量预埋 Bug。
4. 每条反馈记录是否有效、是否进入 Coding、是否生成 PR、是否接受、人工介入、失败原因、耗时和成本。
5. 失败样本不得删除或只统计成功样本。

完成标准：验收 `VAL-*` 中技术项通过且样本完整；指标是否过线在 Phase 9 决策。

### Phase 9：Go / Pivot / Stop

只允许三种结论：

| 结论 | 条件 | 后续 |
|---|---|---|
| Go | 所有 P0 验收通过，样本和价值指标达到第 2 节标准 | 才规划 2.1；可讨论第二个真实 Agent Provider 或更多项目 |
| Pivot | 技术可靠，但使用价值集中在 Context Pack/SDK 或某一小段 | 删除其余平台能力，只保留有效模块 |
| Stop | 样本不足、经常绕过、效率不升反降、安全/恢复不可靠 | 停止平台化，不以“继续优化”替代结论 |

进入最终决策前，`S-*` 至 `OPS-*` 与 `Q-*` 必须全部通过。Owner 决策与独立复核都必须记录。不能用测试全绿、服务在线或完成代码清单代替价值结论。

## 8. 测试与证据策略

### 8.1 测试层次

| 层次 | 必测内容 |
|---|---|
| 单元 | 状态迁移、用户状态映射、PII、策略合并、Schema、幂等键 |
| PostgreSQL 集成 | 乐观锁、唯一约束、Inbox/Outbox、事务回滚、并发编号 |
| Runtime 契约 | submit/inspect/cancel、结构化输出、超时、离线、重复请求 |
| 浏览器 | 两个试点应用中的单实例采集、身份、提交、Mine、验证、销毁 |
| 故障注入 | 响应丢失、外部成功后崩溃、DB 失败、daemon 离线、乱序事件、进程重启 |
| 真实 E2E | 自然反馈→真实 Agent→Draft PR→人工 Merge→真实 CI→测试环境→原反馈人验证 |

### 8.2 统一验证入口

实施过程中补齐以下命令并保持可重复：

```text
Gateway:        mvn verify
SDK/Runtime:    pnpm -r typecheck && pnpm -r test
Compose:        docker compose -f deploy/docker-compose/forgeops-v2.yml config
完整闭环:       scripts/verify-v2-e2e.sh
恢复验证:       scripts/verify-v2-recovery.sh
安全验证:       scripts/verify-v2-security.sh
```

脚本退出 0 只代表其明确执行的断言通过；任何 `skip`、未配置外部系统或未运行环境必须在报告中标为 SKIPPED/ENV_BLOCKED，不得计入通过。

### 8.3 证据格式

每个验收项的证据目录至少包含：

```text
docs/evidence/v2.0/<验收ID>/
├── README.md       # commit、环境、执行人、步骤、观察结果、结论
├── command.txt     # 命令与完整输出，敏感值脱敏
├── query.txt       # 必要的 DB/VCS 查询及结果
└── screenshot.*    # 仅在 UI/Provider 证据必要时提供
```

证据必须绑定 commit SHA、环境和时间。远程 PR/CI 需要保存可复核 URL 与查询结果；仅截图不作为状态一致性的唯一证据。

## 9. 实施纪律

1. 每个 Phase 单独提交，提交中不夹带下一阶段功能。
2. 先补失败测试，再修改实现；每个已识别的 V0.1 缺陷必须有回归用例。
3. 不为通过测试硬编码 Provider 返回值或跳过真实集成。
4. 不同时重写 SDK、状态机和 Runtime；严格按 Phase 顺序减少定位范围。
5. 发现新需求先判断是否属于第 3 节非目标；属于则记录到候选清单，不进入 2.0。
6. 每阶段结束更新验收清单结果和证据路径，不提前勾选。
7. 完成 Phase 8 前，不宣称“2.0 验收通过”或“Personal Workstation 假设成立”。

## 10. 参考边界

- Paseo SDK 用于程序化创建、观察和等待 Agent Run：<https://github.com/getpaseo/paseo/blob/main/public-docs/sdk/index.md>
- Paseo SDK 参考与结构化输出能力：<https://github.com/getpaseo/paseo/blob/main/public-docs/sdk/reference.md>
- Paseo Hub Workflow 自己拥有工作流能力，因此 2.0 明确不与 ForgeOps 状态机叠加：<https://github.com/getpaseo/paseo/blob/main/public-docs/hub/workflows.md>
- Multica 的 Agent/Runtime/Run 模型属于人机工作管理面，保留在 ForgeOps 主链之外：<https://multica.ai/docs/agents>
