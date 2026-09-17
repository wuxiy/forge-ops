# ADR-0012：Engineering Trace 关联模型——cycle 锚定，不建新 ID

- 状态：Accepted
- 日期：2026-09-16
- 决策人：Owner（grill-with-docs 审核会话）
- 关联：ADR-0002（状态机）、ADR-0009（scheduled 事实绑定）；修订 `VER-16`；收敛审核遗留问题第 7 项（Trace 归属）

## 背景

V0.2 提案要求新建全局 Engineering Trace ID 贯穿 Task→Run→PR→CI→Deploy→Runtime。2.0 已有 feedback→cycle 的 ID 链与 `audit_log.trace ID` 字段；新建全局 ID 需解决与 cycle 的映射，形成双真相源风险。

## 决策

1. **不引入新的全局 Trace ID**。关联模型以既有 ID 链为锚：

   ```text
   feedback → cycle → verification_plan → verification agent run / CI check / executor run
                        → verification_evidence → gate 决策 → merge
   ```

   验证层全部实体外键到 `cycle`；进程内关联继续使用 `audit_log` 既有 trace ID。

2. **外部侧携带**：
   - CI：`plan ID` 作为 workflow 入参下发，并在 Check Run output 回传，归一化层校验匹配（防伪造）；
   - 部署：既有 `forgeopsPullRequestNo` payload 绑定（DEL 边界）不变；
   - scheduled 事实：`repo + branch + commit + workflow` 绑定（ADR-0009）。

3. **OpenScope / OTel 后移**：V1 不要求试点 CI 安装 OTel exporter 或任何埋点 agent；外部事件关联完全依靠上述既有字段。

## 影响

- 验收 `VER-16` 收紧：外键锚定同一 Cycle + plan ID 回传校验。
- 提案的 Engineering Trace 作为**展示层概念**保留（未来 OpenScope 视图按本关联模型渲染），作为 ID 体系废弃；词条见 CONTEXT.md。
- 未来引入 OTel 时以 cycle 为锚点关联 span，无需迁移 ID。
