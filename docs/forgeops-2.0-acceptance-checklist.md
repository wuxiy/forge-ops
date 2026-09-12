# ForgeOps 2.0 验收清单

> 依据：[`forgeops-2.0-implementation-plan.md`](./forgeops-2.0-implementation-plan.md)
>
> 状态：待实施、待执行
>
> 日期：2026-09-12

## 1. 验收规则

### 1.1 结果枚举

每项只能填写以下结果之一：

- `PASS`：在指定环境实际执行，所有通过标准命中且证据可复核；
- `FAIL`：已执行但未达到标准；
- `SKIPPED`：主动跳过，不计入通过；
- `ENV_BLOCKED`：受环境阻塞，不计入通过；
- `MANUAL_PENDING`：等待 Owner 或独立复核，不计入通过。

### 1.2 签收边界

1. 所有 `P0` 项必须 PASS，任何 SKIPPED/ENV_BLOCKED 都禁止整体验收。
2. 单元测试不能替代 PostgreSQL、Paseo、Git/VCS、CI 或部署的真实运行证明。
3. 模拟回调不能签收真实 CI/CD 项。
4. 服务启动、端口可达、进程 healthy 不能替代业务闭环。
5. Agent 声称“已创建 PR/测试通过”不能替代 Git Provider 和 CI 的独立查询。
6. Owner 人工 Merge、最终 Go/Pivot/Stop 决策必须人工确认。
7. 每个证据必须绑定 commit SHA、环境、时间和执行人。
8. 历史 V0.1 验收结果不能直接复用为 2.0 PASS。

建议证据路径：`docs/evidence/v2.0/<验收ID>/`。

## 2. 验收总表

| 分组 | 内容 | Gate |
|---|---|---|
| S | 范围、基线、环境 | P0 |
| WF | 数据、Cycle、状态机 | P0 |
| REL | Inbox、Outbox、幂等、恢复 | P0 |
| SEC | 身份、权限、PII、凭证 | P0 |
| EDGE | Registry、SDK 生命周期 | P0 |
| AGT | Paseo Agent 执行 | P0 |
| DEL | Git、CI、部署、验证闭环 | P0 |
| OPS | 部署、观测、重启恢复 | P0 |
| Q | 工程质量与测试 | P0 |
| VAL | 真实样本与价值指标 | Go/Pivot/Stop |

## 3. S — 范围、基线、环境

| ID | 验收项 | 验证与证据 | 通过标准 | 结果 |
|---|---|---|---|---|
| S-01 | 2.0 目标冻结 | 评审实施计划、PR/commit | 目标仅为“真实反馈→受控 Agent→Draft PR→交付→验证”；非目标有明确清单 | 待执行 |
| S-02 | V0.1 历史冻结 | 检查 README/文档引用 | V0.1 文档标识为历史证据；实施不继续修改 V0.1 状态机 | 待执行 |
| S-03 | 基线记录 | 实跑当前 Maven/pnpm 检查并保存输出 | 通过、失败、跳过分别记录；Vue2 skip 不写成 PASS | 待执行 |
| S-04 | 新数据库 | 检查连接信息、Flyway history、表结构 | 使用独立 `forgeops_v2`；迁移从 V1 全新创建 | 待执行 |
| S-05 | 无兼容负担 | 搜索旧状态映射、双写、回填逻辑 | 无 V0.1 数据迁移、旧接口兼容、旧 Multica Issue 续接 | 待执行 |
| S-06 | 试点环境确认 | 记录两仓库、Runtime 主机、Git/CI/测试环境 | 目标、Owner、凭证 Scope、网络边界全部明确；无生产环境 | 待执行 |
| S-07 | Paseo 技术探针 | 隔离 Worktree 实跑 SDK submit/inspect/cancel、结构化输出、超时和断线 | 所有关键契约可实现且有原始输出；失败则停止 2.0，不进入 Phase 1 | 待执行 |
| S-08 | 版本冻结 | 保存 package lock、daemon/Codex 版本与兼容说明 | SDK、daemon、Codex 均有精确版本；后续升级必须重新跑契约测试 | 待执行 |
| S-09 | 指标口径与基线 | 评审记录模板，收集近期历史或同期手工对照 | 指标定义固定；至少 5 条可比基线，无法回溯时已有同期对照方案 | 待执行 |

## 4. WF — 数据、Cycle 与状态机

| ID | 验收项 | 验证与证据 | 通过标准 | 结果 |
|---|---|---|---|---|
| WF-01 | 数据约束 | PostgreSQL Schema/约束查询 | Feedback、Cycle、Context、Run、Inbox、Outbox、Verification、Audit 表与计划一致；关键唯一键存在 | 待执行 |
| WF-02 | 初始 Cycle | 提交一条反馈并查询数据库 | 只创建 Cycle 1，`current_cycle_id` 指向它，Context Snapshot 与其绑定 | 待执行 |
| WF-03 | Reopen 新 Cycle | 完成后执行两次 Reopen | 依次创建 Cycle 2/3；旧 Cycle、Run、Context 不变 | 待执行 |
| WF-04 | 旧结果隔离 | 在旧 Cycle 留下成功 Triage/PR，再 Reopen | 新 Cycle 不读取旧 Cycle 结果，不跳过 Triage，不复用旧 PR | 待执行 |
| WF-05 | Run Attempt | 对同一 Cycle 失败后重试 | 新建 attempt，旧 Run 不覆盖；当前 attempt 唯一 | 待执行 |
| WF-06 | 合法迁移 | 参数化状态测试 | 计划列出的每条合法迁移都有测试并通过 | 待执行 |
| WF-07 | 非法迁移 | 参数化状态测试 + API 调用 | 非法迁移返回明确冲突并写审计，不静默忽略 | 待执行 |
| WF-08 | 失败状态分离 | 分别触发 Coding、Build、Deploy 失败 | 状态为 EXECUTION_FAILED、BUILD_FAILED、DEPLOY_FAILED，且只允许对应恢复路径 | 待执行 |
| WF-09 | 并发控制 | 两个线程同时推进同一 Feedback | 最多一个提交成功；无丢失更新、双 Run 或越级状态 | 待执行 |
| WF-10 | 终态保护 | DONE 后重放旧 CI/Deploy/Run 事件 | 状态不倒退；事件被标记为无效/已过期并留痕 | 待执行 |
| WF-11 | 用户状态纯映射 | 覆盖全部内部状态的测试 | 用户状态由单一函数映射，无第二份可独立写入状态 | 待执行 |
| WF-12 | 编号并发安全 | 并发创建至少 100 条多项目反馈 | 同项目连续且不重复；不同项目前缀与计数隔离 | 待执行 |

## 5. REL — Inbox、Outbox、幂等与恢复

| ID | 验收项 | 验证与证据 | 通过标准 | 结果 |
|---|---|---|---|---|
| REL-01 | 事务与外部调用分离 | 集成测试、事务日志/代码审查 | 领域事务内不调用 Paseo、Git、CI、文件远程存储；只写状态与 Outbox | 待执行 |
| REL-02 | Outbox 原子性 | 故障注入：事务回滚 | 状态与 Outbox 同时回滚；外部系统未收到调用 | 待执行 |
| REL-03 | 外部成功响应丢失 | 在 Provider 成功后中断响应并重试 | 最终只有一个外部 Run/操作；Outbox 进入成功 | 待执行 |
| REL-04 | 必填事件 ID | 发送缺失 `externalEventId` 的回调 | 请求被拒绝且不生成随机/时间 ID | 待执行 |
| REL-05 | 重复事件 | 同一事件并发/顺序投递至少 10 次 | Inbox 只有一条事实记录；领域动作只执行一次；响应一致 | 待执行 |
| REL-06 | 乱序事件 | Deploy→CI→Merge 顺序投递 | 后继事件先 DEFERRED；前置完成后自动重放并到达正确状态 | 待执行 |
| REL-07 | 进程重启重放 | Outbox/Inbox 处理中强制重启 Gateway | 重启后继续处理，无丢失、重复或永久 PROCESSING | 待执行 |
| REL-08 | 重试耗尽 | Provider 持续失败超过阈值 | 进入明确失败/人工处理状态；保留次数、错误和下次操作，不无限重试 | 待执行 |
| REL-09 | Reconciler | 制造超时 Run、未发送 Outbox、Deferred Inbox | 一次对账均能恢复或明确标为需人工处理 | 待执行 |
| REL-10 | 可重复人工重放 | 对失败事件执行授权重放 | 重放可审计、仍遵守幂等，不绕过状态前置条件 | 待执行 |

## 6. SEC — 身份、权限、PII 与凭证

| ID | 验收项 | 验证与证据 | 通过标准 | 结果 |
|---|---|---|---|---|
| SEC-01 | 短期身份令牌 | 正常、过期、篡改、错误项目令牌测试 | 只接受合法签名、未过期且 project/scope 匹配的令牌 | 待执行 |
| SEC-02 | Mine 隔离 | 用户 A/B 创建反馈并交叉查询 | A 不能读取、评论、验证或 Reopen B 的私有反馈 | 待执行 |
| SEC-03 | 项目隔离 | 使用项目 A 令牌访问项目 B | 所有读写均拒绝并留审计；不能仅靠 ID 猜测访问 | 待执行 |
| SEC-04 | Callback 签名 | 正确、错误、过期、重放 Webhook | 错误签名拒绝；重放由 Inbox 幂等；不信任请求体 actorType | 待执行 |
| SEC-05 | CORS Allowlist | 来自允许/未允许 Origin 的浏览器请求 | 只允许 Registry 配置 Origin；默认 `*` 不存在 | 待执行 |
| SEC-06 | 全量脱敏 | 在反馈、URL、日志、Console、Reopen、页面字段注入测试秘密 | Context Snapshot、Prompt、日志和 Agent 输入中均找不到原值 | 待执行 |
| SEC-07 | 采集最小化 | 抓取 SDK payload 与网络请求 | Authorization、Cookie、Token、请求体、响应体不采集 | 待执行 |
| SEC-08 | 截图安全 | 默认提交、启用后取消/确认三种路径 | 默认不截图；未经明确确认不上传；日志不含图片内容或可公开地址 | 待执行 |
| SEC-09 | Git 凭证 Scope | 使用 Agent 凭证访问允许和不允许仓库 | 可访问目标仓库；其他仓库与生产凭证访问失败 | 待执行 |
| SEC-10 | 工作区路径隔离 | Prompt 要求读取父目录、其他项目和敏感文件 | 访问被 Runtime/沙箱拒绝并记录；不能靠 Prompt 放宽 | 待执行 |
| SEC-11 | Human Gate | Agent 尝试 Merge、生产部署、伪造人工回调 | 全部失败；状态不推进；有拒绝审计 | 待执行 |
| SEC-12 | 日志无秘密 | 扫描 Gateway/Runtime/Compose 日志和证据包 | 无 Token、Cookie、原始 Prompt、未脱敏 Context、截图内容 | 待执行 |

## 7. EDGE — Project Registry 与 SDK

| ID | 验收项 | 验证与证据 | 通过标准 | 结果 |
|---|---|---|---|---|
| EDGE-01 | Registry Schema | 构造缺字段、未知字段、非法路径配置 | 启用项目配置无效时启动/刷新失败，错误定位到字段 | 待执行 |
| EDGE-02 | 原子刷新 | 刷新期间并发读取；新配置含一项错误 | 读者只看到完整旧版或完整新版；错误刷新不清空/部分覆盖缓存 | 待执行 |
| EDGE-03 | 策略只能收紧 | 项目尝试放宽全局 Merge/路径/凭证策略 | `ResolvedProject` 仍采用更严格结果并记录拒绝原因 | 待执行 |
| EDGE-04 | 日志类型真实性 | 配置尚未实现的日志 Adapter | 启动/刷新失败；不以“配置字段存在”宣称支持 | 待执行 |
| EDGE-05 | 单 Core 实例 | 两个试点应用分别初始化并观察全局包装 | 每个应用只有一个 Collector/Core，失败请求和 Console 各记录一次 | 待执行 |
| EDGE-06 | 生命周期恢复 | start→stop→start、mount→destroy 循环至少 10 次 | fetch、console.error、拦截器、DOM 均完整恢复；无重复采集和监听器泄漏 | 待执行 |
| EDGE-07 | HMR/重复初始化 | 模拟热更新与两次 init | 行为幂等，页面只有一个入口，事件不重复 | 待执行 |
| EDGE-08 | 单一 SDK 发布面 | 检查 workspace、包清单、构建产物和接入文档 | 2.0 只发布 Core/DOM；旧 Vue2/Vue3/React 薄壳已移除或归档且不进入发布物 | 待执行 |
| EDGE-09 | SDK 身份刷新 | 令牌过期后刷新并继续提交/查询 | 自动获取新令牌或明确要求登录；不降级为匿名姓名授权 | 待执行 |
| EDGE-10 | Context 预览一致 | UI 预览与最终脱敏 Snapshot 对比 | 用户看到的待发送字段与实际外发字段一致，服务端二次清理只收紧 | 待执行 |
| EDGE-11 | 两试点浏览器旅程 | 在两个真实试点应用实跑类型检查和浏览器测试 | 两个应用均完成提交、Mine、查看、验证、销毁；无框架专用核心逻辑 | 待执行 |

## 8. AGT — Paseo Agent 执行

| ID | 验收项 | 验证与证据 | 通过标准 | 结果 |
|---|---|---|---|---|
| AGT-01 | 单一工作流 Owner | 架构检查、搜索 Multica/Paseo Hub Workflow | ForgeOps 是状态唯一事实源；无 Multica 评论 Poller，无 Paseo Hub 主流程 | 待执行 |
| AGT-02 | 最小执行接口 | 契约测试 | 只需 submit/inspect/cancel 完成调用；调用方不依赖 Paseo 类型或评论文本 | 待执行 |
| AGT-03 | 内部鉴权 | 从允许/非允许主机调用 Runtime | 非法服务凭证或来源被拒绝；接口不暴露公网 | 待执行 |
| AGT-04 | Submit 幂等 | 相同 idempotencyKey 并发提交 10 次 | 返回同一 ForgeOps/Provider Run；Paseo 只创建一次 | 待执行 |
| AGT-05 | Triage Schema | 正常输出、缺字段、额外字段、非法 enum | 只有完整合法结果推进；其他进入 INVALID_OUTPUT | 待执行 |
| AGT-06 | Coding Schema | 正常 PR、NO_CHANGE、FAILED、伪造 PR 输出 | Schema 正确分类；伪造结果不能直接推进 PR_READY | 待执行 |
| AGT-07 | 独立 PR 核验 | Agent 返回错误 repo/branch/commit/PR URL | `DeliveryEvidence` 拒绝，记录证据不一致 | 待执行 |
| AGT-08 | 超时与取消 | 运行长任务后超时/人工取消 | Provider Run 确认停止；状态可重试；Worktree 与审计结果明确 | 待执行 |
| AGT-09 | daemon 离线 | Submit 前离线、Run 中断线、恢复上线 | Run 排队或进入可恢复状态；恢复后仅继续/重试一次，无重复 | 待执行 |
| AGT-10 | Worktree 隔离 | 同仓库并发两个 Coding Run | 分支、目录、改动互不污染；均从预期 base commit 开始 | 待执行 |
| AGT-11 | 项目并发限制 | 超过项目并发上限提交 | 多余 Run 保持 QUEUED，不抢占或覆盖工作目录 | 待执行 |
| AGT-12 | 旧 Multica 路径移除 | 代码与 Schema 搜索、业务实跑 | 主链无 `MulticaPoller`、评论 Marker 和 `multica_issue_id` 依赖 | 待执行 |

## 9. DEL — Git、CI、部署与验证闭环

| ID | 验收项 | 验证与证据 | 通过标准 | 结果 |
|---|---|---|---|---|
| DEL-01 | 真实 Draft PR | 自然反馈驱动一次 Coding Run | Provider 存在 Draft PR；repo/base/branch/commit/cycle 全部匹配 | 待执行 |
| DEL-02 | Agent 终点 | 检查 PR 和分支状态 | Agent 只创建分支、Commit、Draft PR；未 Merge、未部署 | 待执行 |
| DEL-03 | 人工 Merge 证明 | Owner 在 Git Provider Merge，保存身份查询 | Merge actor 为授权用户；Webhook 签名真实有效 | 待执行 |
| DEL-04 | Commit 绑定 | 对不同 commit 发送 CI/Deploy 成功 | 非当前 Cycle PR head commit 的事件不推进状态 | 待执行 |
| DEL-05 | 真实 CI 成功 | Git Provider/CI 实际运行 | 对应 commit 的真实流水线成功后进入 DEPLOY_RUNNING | 待执行 |
| DEL-06 | 真实 CI 失败/重试 | 制造一次失败并重新运行成功 | 先 BUILD_FAILED；重试成功后正确恢复，不新建错误 Cycle | 待执行 |
| DEL-07 | 真实测试部署成功 | 部署到非生产环境并查询版本/commit | 实际环境运行同一 commit 后进入 WAITING_VERIFY | 待执行 |
| DEL-08 | 部署失败/重试 | 制造部署失败再恢复 | 先 DEPLOY_FAILED；恢复后进入 WAITING_VERIFY，不误记 BUILD_FAILED | 待执行 |
| DEL-09 | 事件重复/乱序 | 重放真实 Payload 并交换到达顺序 | 结果与正常顺序一致；无丢失、双推进或错误完成 | 待执行 |
| DEL-10 | 验证通过 | 原反馈人用合法身份 PASS | Feedback/Cycle 进入 DONE；Multica/Paseo 状态不被当作最终验收 | 待执行 |
| DEL-11 | 仍有问题 | 原反馈人 REOPEN 并追加新证据 | 新 Cycle 和新 Context Snapshot 创建；旧 PR/Run 保持历史不可变 | 待执行 |
| DEL-12 | 全链审计 | 查询一个完整案例 | Intake、Context、Run、PR、Merge、CI、Deploy、Verify 每步可关联到同一 Feedback/Cycle/commit | 待执行 |

## 10. OPS — 部署、可观测与恢复

| ID | 验收项 | 验证与证据 | 通过标准 | 结果 |
|---|---|---|---|---|
| OPS-01 | Compose 静态校验 | `docker compose ... config` | 配置可解析；Registry/卷均指向真实路径 | 待执行 |
| OPS-02 | 服务寻址 | 容器内逐一探测依赖 | 跨容器使用服务 DNS/明确 Host 地址，不使用 `127.0.0.1` 误指自身 | 待执行 |
| OPS-03 | 无默认秘密 | 删除 Secret 后启动 | 启动失败并提示缺失项；仓库与镜像无有效默认 Token/密码 | 待执行 |
| OPS-04 | 私网暴露 | 检查端口绑定、防火墙/Tailscale | Gateway/Runtime/Paseo 不直接暴露公网；Runtime 内部接口不可由业务客户端访问 | 待执行 |
| OPS-05 | Fresh Install | 空卷、新数据库、新机器配置启动 | 一次按文档部署成功；迁移完成；健康检查通过 | 待执行 |
| OPS-06 | Gateway 重启 | Run/Outbox/Deferred 处理中重启 | 自动恢复，无重复外部动作和错误状态 | 待执行 |
| OPS-07 | Runtime/daemon 重启 | Coding Run 中分别重启 | Gateway 最终观测到一致结果；超时后可安全重试/转人工 | 待执行 |
| OPS-08 | PostgreSQL 短暂中断 | 处理中断数据库后恢复 | 请求明确失败或重试；无缺少审计的状态推进 | 待执行 |
| OPS-09 | 健康检查语义 | 分别断开 DB、Runtime、Paseo | readiness 正确失败；liveness 不造成无限重启；恢复后状态正常 | 待执行 |
| OPS-10 | 指标可用 | 查询 Metrics/日志 | 能定位状态超时、Run 失败、Outbox 重试、Deferred 事件和人工介入；无敏感内容 | 待执行 |
| OPS-11 | 三个验证脚本 | 实跑 e2e/recovery/security 脚本 | 每个断言有明确输出；未配置项导致非 0 或显式非 PASS | 待执行 |

## 11. Q — 工程质量与测试

| ID | 验收项 | 验证与证据 | 通过标准 | 结果 |
|---|---|---|---|---|
| Q-01 | Gateway 单元测试 | `mvn verify` 报告 | 状态、策略、PII、Schema、映射测试实际执行且通过，skipped=0 | 待执行 |
| Q-02 | PostgreSQL 集成测试 | 使用真实 PostgreSQL/Testcontainers | 事务、约束、并发、Inbox/Outbox 测试实际执行且通过，skipped=0 | 待执行 |
| Q-03 | Runtime 契约测试 | `pnpm` 测试报告 | submit/inspect/cancel、幂等、输出、超时、恢复均执行，skipped=0 | 待执行 |
| Q-04 | SDK 测试 | Core/DOM 单元测试与两试点浏览器报告 | 发布接口均有真实断言；两个试点实际执行；无 echo/skip 占位 | 待执行 |
| Q-05 | 故障注入套件 | 执行恢复脚本和报告 | 计划中的六类故障全部实际注入并验证 | 待执行 |
| Q-06 | 真实 E2E | 执行完整链路 | 使用真实 Paseo、Git、CI、测试部署；模拟项为 0 | 待执行 |
| Q-07 | 构建产物 | Gateway/Runtime/SDK/镜像构建 | 全部可重复构建；锁文件提交；无依赖临时手工安装 | 待执行 |
| Q-08 | 工作区清洁 | 验收后 `git status` 与敏感文件扫描 | 仅预期文件变更；无 Token、私钥、运行缓存、未脱敏证据 | 待执行 |
| Q-09 | 文档一致 | 对照实现复核计划、配置和接入文档 | 状态、接口、命令、端口、环境变量均与实跑一致 | 待执行 |
| Q-10 | 独立复核 | 非主要实现者复跑关键 P0 项 | 复核者从文档可复现；差异均记录并关闭 | 待执行 |

## 12. VAL — 真实样本与价值指标

| ID | 验收项 | 验证与证据 | 通过标准 | 结果 |
|---|---|---|---|---|
| VAL-01 | 两个真实仓库 | Registry、Git URL、技术栈记录 | 至少两个真实仓库；至少一个与 demo-app 非同构 | 待执行 |
| VAL-02 | 自然反馈样本 | 完整反馈清单与来源 | 20～30 条自然产生的反馈；预埋 Bug 单独标记且不计入 | 待执行 |
| VAL-03 | 基线可比 | 使用 Phase 0 的人工样本或同期对照 | 至少 5 条样本；使用相同计时口径，含上下文、Prompt、完成和 Review 时间 | 待执行 |
| VAL-04 | 样本完整性 | 对照 DB、Git、失败记录 | 成功、失败、NO_CODE、NEEDS_INPUT 全部统计，无删除失败样本 | 待执行 |
| VAL-05 | 可 Review PR 比例 | 统计有效 Coding 样本 | ≥60% 无人工重组 Prompt 即产生可 Review Draft PR | 待执行 |
| VAL-06 | 上下文效率 | 对比基线与 2.0 中位数 | 人工准备上下文中位时间下降 ≥50% | 待执行 |
| VAL-07 | 可靠性结果 | 审计异常、重复、卡死、错误终态 | 重复 Run、旧结果复用、永久卡死、错误完成均为 0 | 待执行 |
| VAL-08 | 安全结果 | 安全事件与审计复核 | 越权、跨项目、凭证泄露、已知敏感数据外发均为 0 | 待执行 |
| VAL-09 | 绕过率 | 记录用户是否重新手工准备相同上下文 | ≥70% 有效 Coding 样本不需要绕过 ForgeOps；所有绕过原因有分类 | 待执行 |
| VAL-10 | 成本与维护负担 | 汇总 Token/运行时长/人工运维时间 | 每个接受 PR 的成本和维护时间可见，Owner 判断可接受 | 待执行 |
| VAL-11 | Go/Pivot/Stop 决议 | Owner 决议文档 | 明确选择一种结论并引用指标；不得用“先继续优化”代替 | MANUAL_PENDING |
| VAL-12 | 独立关闭 | 独立复核报告 | 对关键指标和至少 3 个完整样本复核，无未解释差异 | MANUAL_PENDING |

## 13. 单案例最终验收记录模板

```text
Feedback ID:
Cycle ID:
Project / Repository:
Source / Natural or Seeded:
Context Snapshot ID / Hash:
Triage Run ID / Attempt / Result:
Coding Run ID / Attempt / Result:
Branch / Commit / Draft PR:
Merge Actor / Time:
CI Pipeline / Commit / Result:
Deployment / Environment / Version / Commit:
Verifier / Result / Time:
Manual context minutes:
Manual re-prompt count:
Failure and recovery events:
Security exceptions:
Evidence directory:
Conclusion: PASS / FAIL / SKIPPED / ENV_BLOCKED
```

## 14. 最终签收

| 角色 | 责任 | 结论 | 签名/日期 |
|---|---|---|---|
| 实施者 | 提交逐项结果和证据，不自行豁免失败项 | 待执行 | |
| Owner | 审核范围、Human Gate、价值指标和 Go/Pivot/Stop | MANUAL_PENDING | |
| 独立复核者 | 复跑关键 P0、检查真实外部证据和样本统计 | MANUAL_PENDING | |

最终状态只有在所有 P0 项 PASS、Owner 完成决议且独立复核关闭后，才能写为“ForgeOps 2.0 验收完成”。
