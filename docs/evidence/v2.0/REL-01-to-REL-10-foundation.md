# REL — Phase 2 可靠事件边界实测

- 执行时间：2026-09-14（Asia/Shanghai）
- 环境：本机回环隔离 PostgreSQL 16 `forgeops_v2_probe`；GraalVM Java 21.0.2。
- 执行方式：Gateway 从同一库验证 Flyway V1/V2；显式执行 `IntegrationReliabilityPostgresIT` 与既有 `FeedbackWorkflowPostgresIT`。

已实际通过的基础项：

| 验收 ID | 实测证据 | 结果 |
|---|---|---|
| REL-01 | `FeedbackWorkflow` 只在领域事务中写 Feedback、Cycle、Context、Audit 与 Outbox；`OutboxDispatcher` 领取事务与 `OutboxPublisher.publish` 分离。默认 Publisher 失败关闭，不调用任何远端 | PASS（当前边界） |
| REL-02 | 对 Context Snapshot 注入非法 JSON；真实 PostgreSQL 拒绝提交，`feedback` 与 `outbox_event` 总数均保持提交前数值 | PASS（数据库回滚） |
| REL-04 | 缺失 `externalEventId` 的 InboundEvent 被 `IllegalArgumentException` 拒绝，未生成替代 ID | PASS |
| REL-05 | 同一 `source + externalEventId` 并发投递 10 次；唯一约束实际触发，最终只有一条 Inbox 记录，10 个响应持有同一 eventId 且均为 `DEFERRED` | PASS |
| REL-08 | 默认 Publisher 精确调度目标 Outbox 事件并失败；该事件保存为 `RETRYING`、attempts=1、错误摘要与下一次重试时间 | PASS（首次失败与退避） |

本阶段已建立但尚不能签收的完整验收：

- REL-03：必须在真实 Paseo Provider 成功后人为丢失响应，再证实持久幂等只产生一个远端 Run；依赖 Phase 5 Runtime。
- REL-06：已具备 RECEIVED/DEFERRED 重放入口，但尚无真实 Git/CI/Deploy 事件处理器，不能证明乱序事件最终到达正确领域状态。
- REL-07：已具备超过五分钟的 `DISPATCHING` 回收路径，但尚未对运行中的 Gateway 做中断/重启注入。
- REL-08：尚未连续失败至最大次数并验证终态与人工处理动作。
- REL-09：Reconciler 已处理遗留 `DISPATCHING` Outbox 及 RECEIVED/DEFERRED Inbox；超时 Agent Run 和跨系统状态不一致需在 Runtime/交付证据链接入后实测。
- REL-10：尚无身份授权层，不能开放人工重放；该入口必须在 Phase 3 权限模型完成后实现并审计。

因此本文件只作为可靠性基础的实测记录，**不将整个 `REL-*` 分组标记为通过**。

## 2026-09-17 增量签收（commit 390f629/1c8046c）

- REL-03 PASS：外部成功响应丢失→重投同 idempotencyKey 返回同一 Run（runtime `service.test.mjs` 幂等+重启用例；每 Coding Run 独立 worktree 复用同一目录）。
- REL-06 PASS：`rel06outOfOrderDeployDefersThenReplays…`：Deploy 先到 DEFERRED，前置完成后 `inbox.apply` 重放到达 WAITING_VERIFY。
- REL-07 PASS：`verify-v2-recovery.sh ops06/ops07`：Gateway kill -9 与 Runtime 重启后无丢失/重复/永久中间态。
- REL-09 PASS：Reconciler 全链（含 `reconcileStuckPlans`、`recoverAbandonedDispatches` 阈值可配）；fail-closed 用例已在基线。
- REL-10 PASS：`inbox.apply(eventId)` 授权重放可审计且仍过状态前置（rel06 用例断言）。
