# ADR-0005：确定性规则引擎允许 autoMerge

- 状态：Accepted
- 日期：2026-09-16
- 决策人：Owner（grill-with-docs 审核会话）
- 关联：ADR-0001、ADR-0002；修订 `docs/forgeops-2.0-implementation-plan.md` 决议 #6、第 3 节"明确不做"中的"自动 Merge"、验收 SEC-11 / DEL-02 / DEL-03、第 2 节成功标准 Human Gate 行

## 背景

2.0 决议 #6 规定 Merge、生产部署和最终验收只能由授权用户完成；成功标准要求 Human Gate 0 次自动 Merge。V0.2 提案的 Quality Gate（PASS → Merge/Deploy）与此冲突。Project Registry 已预留 `policy.autoMerge` 布尔策略位（全局/项目两层、项目只能收紧，当前恒 false 且无消费方）。

## 决策

**修订决议 #6：确定性规则引擎（非 LLM）在全部门禁条件满足时可以机器身份完成 Merge。**

放行的必要条件（全部满足，缺一不可）：

1. 项目策略 `autoMerge` 解析为 true（全局默认 false；项目层只能收紧不能放宽）；
2. 当前 Cycle 的 Gate 决策为 PASS（确定性规则引擎产出，WARN 不放行）；
3. 全部必需验证证据（`requiredCheckName` 精确绑定的 PR 验证 Check）为 PASS，且证据绑定当前 PR head SHA；
4. 目标为非生产分支/环境；生产部署与最终验收（原反馈人 PASS/REOPEN）仍必须人工。

硬性禁令：

- **LLM 的任何输出（Planner 建议、Triage 置信度、"Evidence Confidence"）不得作为放行输入**；LLM 只解释与建议；
- 机器 Merge 的 actor 必须是显式机器身份（专用 GitHub App/Token），在审计与 DEL-03 证据中与人工 Merge 可区分；
- autoMerge 只作用于 ADR-0002 的 PR 门禁路径，不作用于 `WAITING_VERIFY → DONE`。

## 影响

- Human Gate 语义收窄为"生产部署与最终验收"；实施计划与验收清单的相应条目需按本 ADR 改写。
- 试点期保持 `autoMerge=false`，直到按验收清单 `VER-14`（≥3 个真实 PR 以人工 Merge 完整跑通门禁并经独立复核）后才可逐项目开启；开启本身是受审计的配置变更；机器 Merge 演练与四类负向用例见 `VER-13`/`VER-15`。
- `policy.autoMerge` 从预留字段变为活跃策略，`ProjectCatalog` 策略合并测试需覆盖。
