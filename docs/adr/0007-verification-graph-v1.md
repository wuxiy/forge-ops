# ADR-0007：Verification Graph V1——文件/模块级、registry 能力层、单调收紧

- 状态：Accepted
- 日期：2026-09-16
- 决策人：Owner（grill-with-docs 审核会话）
- 关联：ADR-0001、ADR-0002、ADR-0006；收敛审核遗留问题第 2、5 项；替代 V0.2 提案第 7、8 节的 13 种节点全量图谱

## 背景

Verification Graph 是影响集计算与 Risk Engine 的输入，提案将其列为"核心技术壁垒"，但只定义了节点类型清单，未定义边的类型、方向、来源与可信度。三个风险：静态分析产不出业务能力层边；LLM 推断边可能污染放行方向；符号级 AST 分析要求 ForgeOps checkout/编译项目源码，控制面新增大额算力与工具链面。

## 决策

### 1. V1 精度：文件/模块级

- 节点 V1：`Project`、`Module`、`Path`、`TestSuite`（绑定 Check 名称，与 ADR-0006 分类映射同源）、`BusinessCapability`（`BusinessRule` 为其属性，不设独立节点）。
- 符号级节点（`CodeSymbol`/`Endpoint`/`Event`/`DatabaseTable`）与调用图延后：出现真实漏判案例后，作为隔离执行器里的分析任务实现，并需新 ADR。
- Gateway 从 Git Provider API 取 file-level diff，**不 checkout 源码、不上 AST**，零新增算力面。

### 2. 边模型与 provenance

| 边 | 方向 | provenance |
|---|---|---|
| `CONTAINS` | Module → Path | STATIC |
| `COVERS` | TestSuite → Module | STATIC（由 registry Check 映射推导，外部事件自称不采信） |
| `REALIZED_BY` | BusinessCapability → Module | HUMAN（registry） |
| `INVOLVES` | Incident(REOPEN) → BusinessCapability | SYSTEM（由 forgeops_v2 feedback/cycle 数据派生，随 REOPEN 样本自动积累） |

每条边必须携带：类型、方向、**provenance（`STATIC | HUMAN | LLM | SYSTEM` 四值枚举）**、来源引用（registry 文件、API 查询或 agent run id）、创建时间。

### 3. 能力层：registry 声明

`BusinessCapability`/`BusinessRule` 及能力→模块映射、能力 `criticality` 在项目 registry YAML 声明，享有既有纪律：版本控制、Schema 校验、原子刷新、Owner 维护、变更审计、无效即拒绝启动。**V1 不设 LLM 标注通道**；未来若引入"LLM 提议 + 人工审核"，需新 ADR。

### 4. 单调收紧（代码安全轨，不可配置）

provenance=`LLM` 的边在计算中只能收紧、不能放宽：

- 影响集计算：只能做并集扩张（增加候选模块/测试），不能缩小；
- Risk Engine：只能提升风险档，不能降低或豁免阻断。

`STATIC`/`HUMAN`/`SYSTEM` 边正常参与。与"项目只能收紧"（EDGE-03）及 ADR-0006 安全轨同构。

### 5. 保守回退（fail-safe，不 fail-open）

图谱缺失、解析失败或基线过期时：验证计划回退为按 `qualityPolicy` **全量必需类别执行**，验证强度不降、门禁主链不阻断；回退事实落审计并计入指标（OPS-10 可观测）。

### 6. 存储与新鲜度

- `forgeops_v2` 内 `verification_node`/`verification_edge` 表（JPA + 属性 JSONB），不引入图数据库。
- 基线快照绑定项目 base branch commit；落后 N 个 merge 或 T 天即判过期（N/T 为 registry 项目配置，默认值代码固定）。
- PR 影响集 = 基线快照 + file diff 就地计算，**不物化 PR 级图谱**。
- 图谱刷新是受审计的后台任务，不在门禁同步路径上。

### 7. Risk Engine V1 因子（均确定性可得）

`change.size`（diff 统计）、path 类型（config / DB migration / API 路径）、`capability.criticality`（registry）、REOPEN 历史（SYSTEM 边）、Check 失败历史（SYSTEM 累积）。规则引擎输出，LLM 仅解释。

## 影响

- `ProjectCatalog` Schema 扩展 `capabilities` 与图谱新鲜度配置（EDGE-01..03 纪律延伸）。
- 验收清单新增 `VER-18`～`VER-20`（影响集正确性、单调收紧负向、保守回退）。
- 历史覆盖率（test→symbol 映射）不在 V1：影响集精度受限于模块粒度，由保守回退兜底；升级符号级时一并引入。
- 提案的"Quality Graph"称谓废弃，统一叫 Verification Graph（见 CONTEXT.md）。
