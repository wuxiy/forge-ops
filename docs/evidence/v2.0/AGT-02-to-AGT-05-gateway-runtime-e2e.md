# AGT — Gateway → Runtime → Paseo Triage 闭环（局部）

- 执行时间：2026-09-14（Asia/Shanghai）
- 环境：Gateway、Runtime 和 Paseo 均只绑定本机；Gateway 使用隔离 PostgreSQL 16、独立 Registry 和临时 Git 仓库。未连接真实 Git Provider、CI、部署或生产环境。

实际执行：

1. 以有效项目 Token 向 Gateway 提交一条不含敏感信息的 Triage-only 反馈，HTTP 201；
2. Gateway 在同一数据库事务创建 Feedback、Cycle、Context Snapshot、`TRIAGE` AgentRun 和 `AGENT_RUN_REQUESTED` Outbox；
3. Outbox 在事务外调用 Runtime；Runtime 创建真实 Paseo Agent 和独立 worktree；
4. Paseo 返回符合 Triage Schema 的 JSON，Runtime 从 canonical timeline 合并输出片段；
5. Gateway 只接受精确的八字段 Triage 结构（`decision`、`summary`、`rootCause`、`evidence[]`、`relatedFiles[]`、`missingInformation[]`、`risks[]`、`suggestedPlan[]`），持久化脱敏后的 AgentRun 结果，并将 Feedback 转到 `NO_CODE_REQUIRED`；受保护 API 最终显示 `WAITING_VERIFY`。

观察结果：

| 验收 ID | 实测证据 | 结果 |
|---|---|---|
| AGT-02 | Gateway 只经 `AgentExecution` 的 submit/inspect/cancel 边界调用 Runtime；Paseo 类型未进入 Workflow | PASS（当前 Triage 主链） |
| AGT-03 | 无浏览器 Token 为 401；Gateway→Runtime 使用独立服务 Token；Runtime 只绑定 loopback | PASS（本机拓扑） |
| AGT-04 | Gateway 使用 AgentRun/Outbox Idempotency Key；Runtime 的 10 并发真实 Submit 已证明只创建一个 Provider Run | PASS（单 Runtime 实例） |
| AGT-05（正向） | 真实 Triage 返回完整八字段、`NO_CODE_REQUIRED`；Gateway 精确校验、脱敏并推进至 `NO_CODE_REQUIRED` | PASS（正向路径） |
| AGT-10（局部） | 原临时仓库仍在 `main` 且工作区无改动；Paseo 的 Agent 在独立 worktree 运行 | PASS（单任务） |

验证记录：

- Gateway 单测：338/338 通过；
- Gateway PostgreSQL 集成测试：`FeedbackWorkflowPostgresIT`、`IntegrationReliabilityPostgresIT`、`FeedbackSecurityPostgresIT` 共 6/6 通过；
- Runtime 类型检查与测试：2/2 通过；
- Runtime 持久化文件只含 idempotency key、项目、角色、cwd、Provider Run、状态和时间戳；不含 Prompt、Context 或结果。
- `AgentContractsTest` 额外验证：Triage 缺字段、额外字段、未知 decision 被拒绝；即使 Agent summary 返回邮箱或 Token 形态文本，Gateway 持久化前会脱敏。
- 在完整八字段契约与输出脱敏均生效后，已重启最新 Gateway/Runtime 并再次执行真实链路（Feedback `7222274a-c9a2-4506-af8b-80cbcbabd579`）；数据库最终为 `NO_CODE_REQUIRED | TRIAGE | SUCCEEDED | DELIVERED`，`result_json` 含精确八字段，受保护 API 返回 `WAITING_VERIFY`。

未签收：

- AGT-05 的缺字段、额外字段、非法 enum 反例尚未做真实 Paseo 反向探针；
- Coding、Git Provider 独立核验、CI、测试部署、超时、离线恢复、每项目并发上限均尚未完成；
- 本记录不能替代两个真实试点、自然反馈与 Owner 验收，因此不签收整个 `AGT-*` 或 2.0。

## 2026-09-17 增量签收（commit 390f629）

- AGT-01 PASS：Gateway 为唯一状态源（FeedbackWorkflow 独占状态迁移）；无 Multica Poller/评论 Marker/Hub Workflow 主流程。
- AGT-06 PASS：Coding Schema 严格校验 + 伪造 PR 输出不得直接进入 PR_READY（DeliveryEvidence 独立核验链 + PG IT）。
- AGT-07 PASS：`GitHubPullRequestEvidenceMatcher`/`DeliveryEvidencePostgresIT`：repo/branch/commit/PR 不匹配即拒绝。
- AGT-08 PASS：runtime 超时仅在 Paseo 确认取消后 TIMED_OUT（`service.test.mjs`）；取消返回快照。
- AGT-09 PASS：daemon 离线时 Runtime 将 Run 落回 QUEUED（PASEO_UNAVAILABLE，5s 连接超时上限），恢复后仅继续一次（幂等重投）；真实 daemon 通道项见 VER-05/06 ENV_BLOCKED 说明。
- AGT-10 PASS：每 Coding Run 独立 git worktree + `forgeops/run-*` 分支（`AgentRunDispatcher.ensureCodingWorktree`），重投复用同一 worktree。
- AGT-11 PASS：超出项目并发上限的提交保持 QUEUED（runtime 并发用例）。
