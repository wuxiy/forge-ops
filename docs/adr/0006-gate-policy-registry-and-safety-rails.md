# ADR-0006：Gate 规则 v1——registry qualityPolicy + 代码安全轨

- 状态：Accepted
- 日期：2026-09-16
- 决策人：Owner（grill-with-docs 审核会话）
- 关联：ADR-0002、ADR-0005；支撑验收清单 `VER-12`、`VER-13` 的负向用例构造

## 背景

Gate 决策（PASS/WARN/BLOCK）需要可判定的规则载体，否则 VER-* 的通过标准无法构造。候选：registry 每项目声明、全部代码固定、或规则 DSL。决议 #4 的克制原则排除 DSL（引入需要自测的解释器面）；全代码固定则换项目/调阈值都要发版。

## 决策

**规则分两层：registry 声明项目策略，代码固定安全轨；两层缺一不可，安全轨不可配置。**

`qualityPolicy`（registry 项目配置，与 `autoMerge`/`productionDeploy` 同层，项目只能收紧）：

| 字段 | 含义 |
|---|---|
| `requiredChecks` | 必需的 PR 验证 Check 分类及最小数量（unit / api / e2e …），由 Check 名称前缀映射声明 |
| `blocking` | 阻断阈值（如 `security_critical: 0`） |
| `evidenceRetention` | 验证证据保留期限 |

代码安全轨（不可配置、不走 registry）：

1. Gate 输入只能来自 Inbox 核验过的事实与 `qualityPolicy` 配置；LLM 任何输出（计划建议、置信度）禁止进入；
2. 证据必须绑定当前 PR head SHA 且未过期（同 PR 新 push 即全部失效）；
3. Check 的**分类由 `requiredChecks` 的名称映射在归一化层推导，外部事件自称的分类不采信**（防伪造）；
4. WARN 不触发 autoMerge；PASS 要求全部必需分类满足且无阻断项；
5. 决策确定性：相同输入（证据 + 配置版本）必产出相同决策。

## 影响

- `ProjectCatalog` 需扩展 qualityPolicy 的 Schema 校验、原子刷新与"只能收紧"合并（EDGE-01..03 纪律延伸），Registry 无效时拒绝启动。
- VER-12（确定性重放）、VER-13（四类负向）按本 ADR 构造：负向用例分别打击安全轨第 1/2/4 条与策略层。
- 未来阈值调整是配置变更（受审计），不是代码发布；但安全轨变更必须走 ADR。
