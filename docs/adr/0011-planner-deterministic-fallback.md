# ADR-0011：Planner 可用性与成本——确定性回退，LLM 是增强不是依赖

- 状态：Accepted
- 日期：2026-09-16
- 决策人：Owner（grill-with-docs 审核会话）
- 关联：ADR-0006（qualityPolicy）、ADR-0007（图谱与保守回退）、ADR-0013（Paseo 验证角色）；新增 `VER-28`；收敛审核遗留问题第 7 项（Planner 成本）

## 背景

PR 门禁在每次 push 后通过统一 `AgentExecution`/Paseo 触发一次 Verification run（Planner）。通道不可用、超时、输出非法重试耗尽、或预算达限时，门禁主链不能被建议性能力劫持。同时 LLM 调用是持续成本，需要计量与上限。

## 决策

1. **确定性回退，不阻断**：出现以下任一情形时，自动回退为**确定性计划**——由图谱影响集（ADR-0007）+ `qualityPolicy` 全量必需类别生成，语义等同保守回退：
   - Paseo Verification 角色不可用 / 超时 / `INVALID_OUTPUT` 重试耗尽；
   - 项目或全局 token 预算达限。
   回退事件留审计并计入指标；主链照常推进到 Gate 判定。
2. **计量与预算**：token 用量按项目计量，进 OPS-10 与 VAL-10；预算全局默认、项目层只能收紧；达限自动降级（同上），不做硬失败。
3. **缓存**：计划缓存键 = `repo + base + head tree SHA`；rebase/重 push 后 tree 不变则复用计划，不重复计费；tree 变化即失效（与 VER-02 新鲜度一致）。
4. 该决策使验证层在 **LLM 完全不可用时仍可运行门禁**——Planner 提供的是计划质量增强，不是可用性前提。

## 影响

- 验收新增 `VER-28`（回退触发四情形 + 缓存复用）。
- "AI 负责规划、确定性系统负责判定"原则在本 ADR 补全第三段：**规划本身也可确定性降级**。
- 预算与降级事件计入 OPS-10，VAL-10 的成本口径含 Planner token 成本。
