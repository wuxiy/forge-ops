# ADR-0002：验证证据先接入 PR 合并前（PR 门禁先行）

- 状态：Accepted
- 日期：2026-09-16
- 决策人：Owner（grill-with-docs 审核会话）
- 关联：ADR-0001、ADR-0004、ADR-0005；修订 `docs/forgeops-2.0-implementation-plan.md` 第 5.3 节状态模型

## 背景

验证层证据有两个候选接缝进入 2.0 状态机：

1. **PR 合并前**：`PR_READY` 之后、Merge 之前，基于 PR head 的 Check Run 证据执行验证计划并给出 Gate 决策；
2. **部署后**：`WAITING_VERIFY` 阶段对 `testEnvironment` 自动验证，产出证据辅助原反馈人 PASS。

接缝 1 动主链（Human Gate 相关验收需重定义）但价值前置；接缝 2 不动主链、复用已验证的 DEL 部署边界，风险最小。

## 决策

**先做接缝 1（PR 门禁 + autoMerge），接缝 2 作为第二切片延后。**

1. 状态机在 `PR_READY` 与 `BUILD_RUNNING` 之间插入验证阶段：

   ```text
   PR_READY
     → VERIFY_RUNNING
         ├─ VERIFY_FAILED（验证失败/不可信，可重试或转 Coding 修正）
         └─ GATE_PASS
             ├─ autoMerge 策略开启 → 机器身份 Merge → BUILD_RUNNING（ADR-0005）
             └─ autoMerge 关闭    → 等待授权用户人工 Merge → BUILD_RUNNING
   ```

2. PR 阶段验证证据经 `IntegrationInbox` 进入，复用 DEL-01..09 已验证的绑定纪律：仅接受签名 Webhook、`requiredCheckName` 精确匹配、repo/PR/head SHA 三重绑定、`externalEventId` 幂等、乱序 `DEFERRED`。
3. Check Run 证据区分为两类来源并在事件归一化时保留上下文：**PR 验证 Check**（门禁输入，按 ADR-0004 在项目 CI 或自建执行器产生）与 **合并后 CI Check**（现有 DEL-05 语义，驱动 `BUILD_RUNNING`）。两类不得互相冒充。
4. Gate 决策（PASS/WARN/BLOCK）由确定性规则引擎产生（见 ADR-0005 的 LLM 禁令），作为进入 Merge 的前置条件；WARN 不阻断人工路径。
5. 验证计划绑定 PR head SHA：同一 PR 新 push 使旧计划与旧证据过期（沿用"过期事实拒绝、不倒退状态"的既有规则）。

## 影响

- `WF-06/07` 参数化迁移测试需覆盖新状态与全部拒绝路径；新增 Flyway 迁移。
- `DEL-03` 由"人工 Merge 后进入 BUILD_RUNNING"改写为"Gate PASS 且（autoMerge 启用时机器 Merge / 否则授权用户人工 Merge）后进入 BUILD_RUNNING"；`DEL-02` 的"Agent 终点"表述收窄为"Coding Agent 终点是 Draft PR，验证后 Merge 见 ADR-0005"。
- 原反馈人 `WAITING_VERIFY → DONE` 的人工验证路径本期不变（接缝 2 延后）。
