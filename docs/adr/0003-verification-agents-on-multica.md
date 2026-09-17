# ADR-0003：验证类 Agent 经 Multica 通道执行

- 状态：Superseded by ADR-0013
- 日期：2026-09-16
- 决策人：Owner（grill-with-docs 审核会话）
- 关联：ADR-0001；修订 `docs/forgeops-2.0-implementation-plan.md` 决议 #2、#3、#4

> 2026-09-16 后续决议：双执行通道未能证明其相对 Paseo 的不可替代价值，且会提前引入第二套生命周期、凭证与运维面；本 ADR 已由 ADR-0013 取代，仅保留为决策历史。

## 背景

V0.2 提案要求 Verification Agent（生成 Verification Plan）与 Failure Analysis Agent（失败分类），原文将其挂在 Multica 下。2.0 决议 #2/#3 规定 Paseo 是唯一 Agent 执行器、Multica 不进主链，Phase 5 刚刚完成 MulticaPoller/评论标记的移除（AGT-12）；决议 #4 同时预留了逃生条款："出现第二个真实实现需求后再抽取 Adapter seam"。

## 决策

**修订决议 #2/#3：Multica 承载验证类 Agent，与 Paseo 分工并存。**

1. 角色分工：
   - **Paseo**：继续执行 `TRIAGE`、`CODING`（需要仓库 Worktree 的 Agent Run），不变；
   - **Multica**：执行 `VERIFICATION`（Planner）与 `FAILURE_ANALYSIS`（Triage 失败分类）角色，输入为 ForgeOps 库内已有事实（Diff、图谱、证据），输出为固定 JSON Schema。
2. `AgentExecution` 按决议 #4 的逃生条款抽取 Adapter seam，形成两个受控实现（paseo / multica-verify）；不建 Provider 注册中心，新增第三个实现仍需 ADR。
3. 边界不因通道放宽：
   - ForgeOps 数据库仍是状态唯一事实源；Multica 侧任何状态不得回写主链；
   - Multica 返回结果只是声明，必须符合代码固定的 JSON Schema，`INVALID_OUTPUT` 不推进状态；
   - 幂等键 `feedbackId/cycleId/role/attempt` 规则与 Paseo 通道一致；超时、取消、离线恢复、每项目并发同级约束；
   - Multica 凭证为独立服务凭证，最小权限，不得复用 Paseo/Git 凭证。
4. Multica 原有的人机工作管理用途不变，本 ADR 只把"验证类 Agent 执行"纳入主链。

## 影响

- AGT-12"主链无 Multica 依赖"的验收表述收窄为"Coding/Triage 链路无 Multica 依赖"。
- 验收清单需为 Multica 通道补齐与 AGT-* 同级的幂等/Schema/超时/凭证/审计验收项（并入 `VER-*` 组）。
- 决议 #4 的"单一实现"前提消失，`AgentExecution` 接口重构为双实现是本 ADR 的直接成本。
