# 架构决策记录（ADR）索引

> 2026-09-16 由 grill-with-docs 审核会话建立：V0.2 验证层提案（外部输入）经交叉审计后并入 ForgeOps 2.0 实施线，全部方向性决策记录于此。术语见仓库根 [`CONTEXT.md`](../../CONTEXT.md)。

| ADR | 标题 | 一句话 | 修订的 2.0 决议 |
|---|---|---|---|
| [0001](0001-verification-layer-merges-into-2.0.md) | 验证层并入 2.0 当前实施 | 概念采纳、载体重写、试点沿用两 GitHub 仓库、补偿纪律 | — |
| [0002](0002-pr-gate-before-merge.md) | PR 门禁先行 | `PR_READY → VERIFY_RUNNING → GATE_PASS` 插入合并前；`WAITING_VERIFY` 增强延后 | 状态模型 §5.3 |
| [0003](0003-verification-agents-on-multica.md) | 验证类 Agent 走 Multica（已废止） | 被 ADR-0013 取代，仅保留为决策历史 | 决议 #2、#3、#4（已再次修订） |
| [0004](0004-hybrid-test-execution.md) | 混合测试执行 | 单测/API/契约在项目 CI；E2E/性能在自建隔离执行器；不可信代码不上控制面 | 决议 #4（触发第二实现） |
| [0005](0005-deterministic-auto-merge.md) | 确定性规则可 autoMerge | 四必要条件 + LLM 硬性禁令；生产部署与最终验收仍人工；`policy.autoMerge` 激活 | 决议 #6 |
| [0006](0006-gate-policy-registry-and-safety-rails.md) | Gate 规则载体 | registry `qualityPolicy`（项目只能收紧）+ 五条代码安全轨（不可配置） | — |
| [0007](0007-verification-graph-v1.md) | Verification Graph V1 | 文件/模块级、registry 能力层、边带四值 provenance、LLM 边单调收紧、保守回退 | — |
| [0008](0008-e2e-ephemeral-verification-stack.md) | E2E 一次性验证栈 | 每 run 从 PR head 构建隔离 compose 栈跑完即毁；`testEnvironment` 不被预合并污染；失败即失败 | 修订 ADR-0004 表述 |
| [0009](0009-selection-semantics-and-recall.md) | 选择语义与召回度量 | 加法起步、减法默认关；启用需连续 N 次对照召回 100%；漏检自动降级断路器 | — |
| [0010](0010-evidence-llm-boundary-storage-retention.md) | 证据 LLM 边界/存储/保留 | 白名单输入（脱敏文本+截图，域内模型）；文件目录+DB 引用；清理留审计、摘要永久保留 | — |
| [0011](0011-planner-deterministic-fallback.md) | Planner 确定性回退 | LLM 是增强不是依赖；四情形回退确定性计划不阻断；预算项目只能收紧；tree SHA 缓存 | — |
| [0012](0012-trace-anchored-on-cycle.md) | Trace 关联模型 | cycle 锚定不建新 ID；CI 经 plan ID 回传；OpenScope/OTel 后移 | — |
| [0013](0013-single-paseo-execution-multica-deferred.md) | 单 Paseo 默认执行，Multica 延后 | 全部 Agent 角色默认走 Paseo；Multica 退出 P0 主链；第二 Provider 需满足准入门槛 | 决议 #2、#3、#4；取代 ADR-0003 |

配套验收：`forgeops-2.0-acceptance-checklist.md` 第 10 节 `VER-01`～`VER-28`。
