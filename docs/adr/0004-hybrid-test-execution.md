# ADR-0004：混合测试执行——项目 CI + 自建隔离执行器

- 状态：Accepted
- 日期：2026-09-16
- 决策人：Owner（grill-with-docs 审核会话）
- 关联：ADR-0001、ADR-0002；替代 V0.2 提案第 12 节的 LocalRunner/DockerRunner/Testkube 三环境方案

## 背景

V0.2 提案让 ForgeOps 通过 VerificationRunner SPI 直接运行 JUnit/Playwright/Testcontainers 等 8 类引擎，运行环境含"LocalRunner"。PR 代码是不可信输入：在控制面主机执行等于把任意代码执行风险引入 Gateway，且需自备 JDK/Node/浏览器全量工具链。

## 决策

**按验证类型混合执行，不可信代码永不在 Gateway 主机执行。**

| 验证类型 | 执行位置 | ForgeOps 职责 |
|---|---|---|
| 单元 / API / 契约 | 项目自身 CI（GitHub Actions） | 经 workflow 触发下发验证计划；只接收 `requiredCheckName` + head SHA 精确绑定的 Check Run 证据 |
| E2E / 性能等需环境编排 | ForgeOps 自建隔离执行器（Docker 容器级） | 调度、产物采集、证据落库；E2E 指向一次性验证栈（ADR-0008） |

约束：

1. 自建执行器与控制面网络隔离、每任务一次性容器、无共享凭证；执行器是新的攻击面，必须配套安全验收项（沙箱逃逸、网络隔离、产物扫描后入库）。
2. 引擎按真实需求逐个引入（先 JUnit-on-CI，再 Playwright-on-执行器），不建 8 引擎 SPI 注册面；`VerificationRunner` seam 在第二个引擎真实出现时才抽象。
3. Testkube/K8s 是未来评估项，不进入本期。
4. 两种执行语义的产物统一为 Verification Evidence（ADR-0002 的 PR 验证 Check），证据格式在归一化层抹平来源差异。

## 影响

- 决议 #4 的"第二个真实实现"条件由混合模式触发：CI 通道与自建执行器构成两个执行实现，这是可接受的显式例外。
- 自建执行器新增运维面：镜像维护、算力、清理、升级；其成本计入 VAL-10。
- 项目 CI 需要为验证计划提供入参接口（如 workflow_dispatch 携带计划 ID），这是对试点仓库 CI 的侵入性要求，纳入试点接入清单。

> 2026-09-16 修订（ADR-0008）：E2E 执行目标由 `testEnvironment` 修订为一次性验证栈；`testEnvironment` 仅供合并后部署与人工验证。
