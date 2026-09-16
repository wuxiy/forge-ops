# ADR-0001：验证层并入 ForgeOps 2.0 当前实施

- 状态：Accepted
- 日期：2026-09-16
- 决策人：Owner（grill-with-docs 审核会话）
- 输入材料：`~/Downloads/google/forgeops-v0.2-architecture-upgrade.md`（外部提案，下称"V0.2 提案"）

## 背景

V0.2 提案（2026-09-16）为 ForgeOps 增加 Verification Layer：Change Intelligence、Risk Engine、AI Test Planner、Test Impact Analysis、Verification Evidence 与 Quality Gate。该提案以 V0.1（Multica 驱动）为基线撰写，未吸收 2.0 实施计划（2026-09-12）的不可变决议，与现行基线在 Multica 主链、运行时插件平台、自动 Merge、技术栈（MyBatis-Flex、Next.js UI）上正面冲突。

交叉审计结论：提案的核心增量（Change-driven、风险驱动验证、证据化、确定性 Gate）与 2.0 的 `DeliveryEvidence`/`WAITING_VERIFY`/DEL 闭环是同一主题的深化，属于延伸而非分叉；但其落地载体必须重写为 2.0 体系。

2.0 当前处于 Phase 6（真实 Git/CI/部署证据链，仅完成本地受控验证），Phase 8 样本与 Phase 9 Go/Pivot/Stop 决策未做；实施纪律第 4/5 条原则上禁止并行重写状态机、要求新需求先入候选清单。

## 决策

1. **立即并入**：验证层作为 2.0 的范围扩展立即进入当前实施，不等 Phase 9 Go 决策，也不新开独立的 "V0.2" 版本分支。版本口径统一为 2.x，文档与代码不再使用 "V0.2" 指代本线。
2. **概念采纳、载体重写**：采纳提案的验证层概念与设计原则（AI 规划/确定性判定分离、Change-driven、Risk-based、全部验证产生 Evidence）；不采纳其与 2.0 冲突的载体：MyBatis-Flex（沿用 JPA）、React/Next.js UI（2.0 不做前端）、8 引擎 Runner SPI 注册面（按 ADR-0004 以真实需求逐个引入）、提案的仓库结构（沿用 gateway/runtime/sdk 布局）。
3. **试点沿用 2.0 已注册的两个 GitHub 仓库**，复用已受控验证的 Webhook/Check Run/Deployment 边界；不新设 dogfood 仓库；GitLab/akso 适配延后到验证层闭环跑通之后。
4. **补偿纪律**（对冲"未完成 DEL 真实签收即改主链"的风险）：
   - 验收清单先增补 `VER-*` 组（PR 门禁、Gate、autoMerge、混合执行、Multica 验证通道），每项给出可判定通过标准后才开始对应实现；
   - 验证层每个切片单独提交，不与既有 Phase 收尾工作混在同一提交；
   - 既有 P0 项不放宽；状态机变更必须全量回归 WF-06/07 参数化迁移测试。

## 影响

- 实施计划第 1 节决议 #2/#3/#6、第 3 节"明确不做"清单由 ADR-0003、ADR-0005 正式修订，其余决议不变。
- 主链状态机在 DEL 真实签收前变更（ADR-0002），DEL-03/05 的语义需同步扩展；这是本 ADR 接受的显式风险。
- 后续设计文档（Verification Graph 数据模型、边 provenance、TIA 可信度度量）在动代码前完成，见审核会话遗留问题清单。
