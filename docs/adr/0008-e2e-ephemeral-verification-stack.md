# ADR-0008：E2E 执行环境——一次性验证栈

- 状态：Accepted
- 日期：2026-09-16
- 决策人：Owner（grill-with-docs 审核会话）
- 关联：ADR-0002（PR 门禁先行）、ADR-0004（混合执行；本 ADR 细化并修订其 E2E 目标表述）；收敛审核遗留问题第 3 项

## 背景

PR 门禁的 E2E 需要运行 PR head 代码，但 2.0 的 `testEnvironment` 是**合并后**才部署的环境，且同时是测试人员复现反馈的人工验证环境。在共享环境上做预合并 E2E 会污染人工验证场景、需要部署/回滚未验证代码，并与 DEL 部署事件语义（`forgeopsPullRequestNo` 绑定合并后部署）纠缠。

## 决策

### 1. 一次性验证栈（Ephemeral Verification Stack）

执行器为每个验证 run 从 PR head 构建并拉起隔离 compose 栈（应用 + PostgreSQL/Redis/Kafka 等依赖容器 + 种子数据），E2E 在栈内执行，结束即销毁容器、网络与卷；同 commit 可重现。

共享 `testEnvironment` 保持 2.0 原语义：仅供合并后部署与原反馈人人工验证。未来 WAITING_VERIFY 增强切片若要对 `testEnvironment` 做部署后自动验证，需新 ADR（共享环境与人工使用的并发冲突另行解决）。

### 2. 环境定义与种子数据在项目仓库

compose 描述与种子 fixtures 放项目仓库，路径与入口由 registry 白名单声明，随 PR 代码同版本演进（改 schema 的 PR 自然携带新 compose/种子）。ForgeOps 只消费不维护。

compose 文件是**不可信输入**，进入 VER-10 沙箱纪律管辖：出网默认拒绝、外部域名 allowlist 进 registry；镜像经 allowlist/扫描后拉取；栈内不得挂载宿主敏感路径、不得访问控制面网络。

### 3. 并发模型：每项目串行

每项目验证并发 1（同项目验证 run 串行排队、不抢占），全局并发上限可配置——与 Paseo Runtime `FORGEOPS_RUNTIME_MAX_CONCURRENT_PER_PROJECT=1` 的既有纪律同构。

### 4. 失败即失败，显式重试

任一必需证据失败即进入 `VERIFY_FAILED`；重试是显式新 attempt 并留审计。**不自动重跑、不多数决，"重跑到过"不计入 PASS**；flaky 识别后续以历史统计（SYSTEM 数据）标注，不进 V1。门禁确定性语义保持：证据结果与配置唯一决定 Gate 决策。

### 5. 按试点实际情况启用 E2E 类别

仅具备 E2E 套件的试点在 `qualityPolicy` 声明 `e2e` 必需；其余项目从 `unit`/`api` 起步。

## 影响

- ADR-0004 表中"E2E 执行器指向 `testEnvironment`"修订为"指向一次性验证栈"。
- 验收清单新增 `VER-21`～`VER-23`。
- 执行器新增职责：PR head 镜像构建、compose 编排、栈生命周期管理、按项目排队——构成 ADR-0004 所述运维面的主要部分，成本计入 VAL-10。
- 种子数据策略同时给未来的 Traffic Replay（Keploy）留了挂点：录制流量可转为 fixtures。
