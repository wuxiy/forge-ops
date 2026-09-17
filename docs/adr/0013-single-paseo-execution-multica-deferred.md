# ADR-0013：单 Paseo 默认执行，Multica 延后

- 状态：Accepted
- 日期：2026-09-16
- 决策人：Owner
- 关联：ADR-0001、ADR-0003、ADR-0010、ADR-0011；取代 ADR-0003

## 背景

ADR-0003 曾将 Triage/Coding 分配给 Paseo，将 Verification/Failure Analysis 分配给 Multica。重新审核后确认，两个产品都拥有 Agent、Run、Runtime、并发、离线恢复和权限语义；在尚未证明 Multica 提供不可替代能力前，同时进入 2.0 P0 主链会提前引入第二套幂等、取消、超时、凭证、审计、版本兼容和运维面。

验证类 Agent 的输入主要是 ForgeOps 已持久化的 Diff、图谱和证据，并不天然需要第二个工作管理平台。ADR-0011 又保证 Planner 不可用时可确定性回退，因此 Multica 不是 2.0 核心闭环的可用性前提。

## 决策

1. **2.0 默认只有一个 Agent Provider：Paseo。** `TRIAGE`、`CODING`、`VERIFICATION`、`FAILURE_ANALYSIS` 均通过同一 `AgentExecution` 接口和 Paseo Adapter 执行。
2. **ForgeOps 继续拥有工作流。** Provider 只执行一个 Run；状态迁移、attempt、重试、幂等、Gate、CI/CD、最终验证和审计全部留在 ForgeOps。
3. **角色差异由输入和权限表达，不复制 Provider。** Coding 可以使用受控 Worktree；Verification/Failure Analysis 只读取白名单内的 Diff、图谱、脱敏证据，不获得 Git 写凭证、生产凭证或仓库外路径权限。
4. **Multica 退出 2.0 P0 主链。** 它保留为独立的人机工作管理面，可以承载人工创建、分派和观察工作，但其 Issue、评论、Run、状态和自动化均不驱动 ForgeOps。
5. **不做自动 Provider 路由或静默故障切换。** 同一 Run/attempt 不得从 Paseo 静默切换到其他 Provider。未来切换必须创建新 attempt、显式记录 Provider 和原因，并保留旧结果。

## 第二 Provider 准入门槛

Multica 或任何其他第二 Provider 只有全部满足以下条件后，才可提交新的 ADR 申请进入影子或可选通道：

1. 真实试点证明存在 Paseo 或确定性服务无法满足的硬能力，例如可信域模型、多模态证据处理或必须的人机协作审阅；偏好、界面差异或未来可能性不算硬需求。
2. 先以影子模式运行，不驱动状态、不参与 Gate、不影响主链可用性。
3. 通过与 Paseo 同级的固定 JSON Schema、幂等、并发、超时、取消、离线恢复、凭证隔离、数据脱敏和审计验收。
4. 用真实样本证明质量或人工效率增益高于新增的部署、监控、升级和故障处理成本。
5. Provider 故障时确定性回退仍能推进主链；不得把第二 Provider 变成新的单点依赖。

## 影响

- ADR-0003 状态改为 Superseded；其双执行通道方案不再是当前实施约束。
- `AgentExecution` 保持深模块接口，但 2.0 只实现并启用 Paseo Adapter，不建设 Provider 注册中心。
- `VER-04`～`VER-07` 改为验证 Paseo 的 Verification/Failure Analysis 角色，不再要求 Multica 环境或凭证。
- `S-06`、`Q-06` 和试点环境不再把 Multica 作为 P0 依赖。
- Multica 后续若满足准入门槛，必须新建 ADR 和条件验收组，不得直接恢复 ADR-0003。
