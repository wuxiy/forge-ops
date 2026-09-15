# WF — Phase 1 领域与数据库实测

- 执行时间：2026-09-14（Asia/Shanghai）
- 环境：全新隔离 PostgreSQL 16 数据库 `forgeops_v2_probe`，仅绑定本机回环端口；GraalVM Java 21.0.2。
- 执行方式：Gateway 从空库执行 Flyway；随后显式执行 `FeedbackWorkflowPostgresIT`。

已实际通过的基础项：

| 验收 ID | 实测证据 | 结果 |
|---|---|---|
| WF-01 | Flyway 只执行新的 v2 V1；数据库实际存在 Feedback、Cycle、Context、Run、Inbox、Outbox、Verification、Audit、Counter 九张业务表；Cycle、Inbox、Run 的关键唯一约束可查询 | PASS |
| WF-02 | `FeedbackWorkflow.submit` 在真实 PostgreSQL 创建 Cycle 1、绑定 `current_cycle_id`、创建 Context Snapshot，并持久化 `CONTEXT_READY` | PASS |
| WF-03 | 同一案例连续两次 Reopen，真实创建 Cycle 2、Cycle 3；三份 Context Snapshot 分别绑定各 Cycle | PASS |
| WF-04 | 集成测试验证 Cycle 1/2/3 的 snapshot 各为一条、ID 不复用；新 Cycle 成为唯一 current cycle | PASS（Context/Cycle 范围） |
| WF-05 | Triage Run 在真实 PostgreSQL 失败后，重试创建同一 Cycle 的 attempt 2；attempt 1 保持 `FAILED`，attempt 2 为新的 `QUEUED` Run 与新的 Outbox 事实 | PASS |
| WF-06 / WF-07 | 状态矩阵测试覆盖 18 × 18 = 324 种迁移；合法迁移允许，其他组合被拒绝 | PASS |
| WF-11 | 用户可见状态只由 `FeedbackState.userVisibleStatus()` 纯映射产生，数据库无第二个 display-state 字段 | PASS |
| WF-12 | 对同一项目先创建种子反馈，再并发提交 100 条；真实 PostgreSQL 测试得到不重复且连续的 1002–1101 编号 | PASS |

尚不能签收的项目：

- WF-08（各类失败恢复）需要 Phase 2/5/6 的真实故障来源；
- WF-09（同一 Feedback 并发推进）需 API 命令与并发版本冲突测试；
- WF-10（终态事件重放）依赖 Phase 2 Inbox。

因此这份文件不把整个 `WF-*` 分组标为完成；它只记录已经实跑的基础证据。
