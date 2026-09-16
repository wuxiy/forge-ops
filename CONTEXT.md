# ForgeOps 术语表

> 本文件是仓库的领域词汇表：工程产出（Issue 标题、重构提案、测试名、文档）引用以下概念时使用这些词，不使用"避免"列的近义词。
> 已确立条目来自 2.0 实施基线；验证层条目由 ADR-0001..0005 引入（2026-09-16）。决策记录见 `docs/adr/`。

## 反馈与工作流（已确立）

| 术语 | 定义 | 避免 |
|---|---|---|
| Feedback | 自然产生的用户/测试/产品反馈，有稳定身份与项目归属 | 工单、Bug 单（泛指时） |
| Cycle | 一次初始处理或 Reopen 对应的不可复用处理周期，`(feedback_id, cycle_no)` 唯一 | 轮次、会话 |
| Context Snapshot | 每个 Cycle 的不可变脱敏上下文，含 Schema 版本与脱敏统计 | 上下文包 |
| Agent Run | 一次 Triage/Coding/验证类尝试，`attempt` 递增不覆盖 | 任务执行 |
| WAITING_VERIFY | 部署到测试环境后等待原反馈人 PASS/REOPEN 的状态；人工出口本期不变 | 待验收 |
| Draft PR | Coding Agent 的终点产物；经 ADR-0002/0005 后 Merge 前须过 PR 门禁 | — |

## 执行与集成（已确立，ADR 修订处以标注）

| 术语 | 定义 | 避免 |
|---|---|---|
| AgentExecution | ForgeOps 调用 Agent Run 的深模块接口（submit/inspect/cancel）；ADR-0003 起有两个受控实现：Paseo（Triage/Coding）、Multica-Verify（验证类） | Runtime Provider（决议 #4 不建注册中心） |
| Paseo Runtime | `runtime/forgeops-paseo-runtime`，执行需要仓库 Worktree 的 Agent Run | — |
| Multica 通道 | ADR-0003：承载 Verification / Failure Analysis 角色的 Agent 执行通道；与 Multica 人机工作管理面用途并存 | Multica 主链（旧义，已修订） |
| Inbox / Outbox | 外部事件入站事实表与领域事件出站表；`externalEventId` 是幂等去重键 | 消息队列 |
| DeliveryEvidence | 对 PR/Build/Deployment 的**独立查询核验**结果；Agent 声明不得替代 | 交付回执 |
| requiredCheckName | 项目注册的唯一 CI Check 绑定名，任何其他 Check 不能推进状态 | — |
| testEnvironment | 项目注册的非生产测试环境；部署事实须精确匹配；仅供合并后部署与人工验证，不承载 PR 阶段验证（ADR-0008） | staging（泛称）、预合并验证环境 |

## 验证层（ADR-0001..0005 引入）

| 术语 | 定义 | 避免 |
|---|---|---|
| Verification Layer / 验证层 | 2.0 范围扩展：Change-driven、风险驱动的持续验证能力（ADR-0001） | 测试管理平台 |
| PR 门禁 | `PR_READY → VERIFY_RUNNING → GATE_PASS` 的合并前验证路径（ADR-0002） | Quality Gate（单指决策函数时） |
| Gate 决策 | 确定性规则引擎输出的 PASS / WARN / BLOCK；唯一可触发 autoMerge 的来源 | LLM 门禁 |
| autoMerge | registry `policy.autoMerge` 激活后的机器身份 Merge，受 ADR-0005 四条件与 LLM 禁令约束 | 自动合并（无策略语境时） |
| qualityPolicy | registry 每项目门禁策略：必需 Check 分类（名称映射推导，事件自称不采信）、阻断阈值、证据保留；项目层只能收紧（ADR-0006） | 规则 DSL、Quality Gate 配置文件 |
| Verification Plan | 验证计划：Multica Verification Agent 依固定 JSON Schema 产出，或确定性回退生成（ADR-0011）；选测与跳过均须给理由 | 测试计划（传统 TestCase 清单） |
| Verification Evidence | 验证执行的过程与结果证据（区别于 DeliveryEvidence 的交付核验）；全部验证必须产生证据 | 测试报告（单文档义） |
| Risk 分级 | 规则引擎输出的 LOW / MEDIUM / HIGH / CRITICAL；LLM 仅解释不参与计算 | 风险评分（无规则语境时） |
| Failure Triage | 失败七分类：CODE_BUG / TEST_BUG / FLAKY_TEST / ENVIRONMENT / TEST_DATA / DEPENDENCY / UNKNOWN | 失败原因（泛称） |
| Self-Healing 三级权限 | 允许自动改 selector/wait 类；test data/步骤/环境需审；断言与安全/资金/权限/合规规则禁止自动修改 | 自修复（无权限语境时） |
| 混合执行 | 单测/API/契约在项目 CI，E2E/性能在自建隔离执行器；不可信代码不上 Gateway 主机（ADR-0004） | Runner SPI |
| Verification Graph | 节点/边模型 V1：文件/模块级 + registry 能力层，边带 provenance；符号级延后（ADR-0007） | 质量图谱、Quality Graph |
| provenance | 边来源四值枚举：STATIC / HUMAN / LLM / SYSTEM | 可信度分数、置信度 |
| 单调收紧 | LLM 边只能扩大影响集/提升风险，禁止反向（代码安全轨，ADR-0007） | 置信度加权 |
| 保守回退 | 图谱缺失/过期/解析失败按 `qualityPolicy` 全量必需类别执行，不降强度不阻断（ADR-0007） | fail-open |
| 影响集（Impact Set） | 基线快照 + file diff 就地计算的受影响模块/能力/测试集合，不物化 PR 级图谱 | 全量回归集 |
| 基线快照 | 绑定 base branch commit 的图谱版本；落后 N 个 merge 或 T 天即过期 | — |
| 一次性验证栈 | 每 PR 验证 run 在执行器内从 PR head 构建拉起的隔离 compose 栈，跑完即毁（ADR-0008） | preview 环境、临时测试环境 |
| 种子数据 | 项目仓库内随代码同版本演进的 E2E 初始数据 fixtures，registry 白名单声明路径 | 测试库快照、造数脚本（泛称） |
| 显式重试 | 验证失败后的新 attempt，留审计；"重跑到过"不计入 PASS（ADR-0008） | 自动重跑、多数决 |
| 加法选择 / 减法选择 | 加法＝影响集/风险只用于追加类别与升级（V1 默认）；减法＝按影响集少跑（feature flag 默认关，受召回门槛与断路器约束）（ADR-0009） | 测试裁剪、跳过测试（无门控语境时） |
| 选择召回率 | 被选中测试捕获的真实回归 / 全部真实回归（由夜间全量或事后 REOPEN 定义）；启用减法需连续 N 次对照 100% | 召回估计、置信度 |
| 断路器（选择降级） | 任一漏检即自动降级该项目为全量选择并落审计，重新启用需 Owner 复核；不可配置绕过 | 熔断（泛称） |
| 夜间全量兜底 | 试点仓库 scheduled workflow 跑全量套件，事实绑定 repo+branch+commit+workflow（无 PR），与 PR Check 同级核验 | nightly 回归（泛称） |
| LLM 输入白名单 | 验证侧 LLM 只接受脱敏文本摘要与截图；trace/视频/原始 payload 不进；模型须部署于可信域（ADR-0010） | 全量证据投喂 |
| 种子合成纪律 | 一次性栈种子只含合成数据，截图/日志由此天然无真实 PII（ADR-0010） | 脱敏截图（后处理语义） |
| 证据大件 | trace/截图存文件目录、DB 存引用与内容哈希；V1 不采集 video（ADR-0010） | 证据入库（bytea 语义） |
| 确定性回退计划 | Planner 不可用/超时/重试耗尽/预算达限时，由图谱影响集 + 全量必需类别生成的计划；主链不阻断（ADR-0011） | 降级计划 |
| cycle 锚定 | 关联模型：验证层实体外键 cycle，CI 经 plan ID 回传校验，不建全局 Trace ID（ADR-0012） | Engineering Trace ID（提案旧案，已废弃为 ID 体系） |

## 版本口径

- "V0.1" 仅指 2026-09 前的历史材料；"V0.2" 一词废弃，验证层并入 2.0 实施线（ADR-0001）。
