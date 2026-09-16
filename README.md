# ForgeOps 2.0（实验性）

ForgeOps 2.0 是一个受控的工程反馈闭环：反馈经 Gateway 脱敏和持久化后，由 ForgeOps 自己的状态机驱动 Paseo Runtime；Coding Agent 的终点是待验证的 Draft PR，合并前经 PR 门禁，确定性 Gate 在满足 ADR-0005 全部条件时可机器 Merge，生产部署与最终验收仍必须人工。

当前实现仍处于实验验证期，尚未完成真实 Git/CI/测试部署、两个真实试点和自然反馈价值样本。因此不能作为发布级平台或生产自动化使用。

## 边界

```text
Feedback SDK / Host identity
          │
          ▼
ForgeOps Gateway ──service credential──► forgeops-paseo-runtime ──► Paseo daemon
      │                                             │
      ├──service credential──► Multica 验证通道（ADR-0003，未实施）
      ├──受控调度──► 隔离执行器 / 一次性验证栈（ADR-0004/0008，未实施）
      ├─ PostgreSQL: Feedback / Cycle / Context / Run / Inbox / Outbox
      └─ Git/CI/Deploy evidence (尚未接入真实 Provider)
```

- Gateway 是状态机、重试和审计的唯一事实源；Paseo 是 Coding/Triage 执行器，Multica 验证通道与隔离执行器同样只是执行器，不是状态源。
- Runtime 只提供内部的 `submit`、`inspect`、`cancel`，默认只绑定 loopback。
- 浏览器不能传入 Agent Provider、工作目录、权限、超时或输出 Schema。
- Triage/Coding 输出必须满足代码固定的 JSON Schema；Coding 声称的 PR/Commit 不能直接推进为 `PR_READY`。
- Multica 用于人工工作管理，并承载验证类 Agent（ADR-0003）；Coding/Triage 仍走 Paseo。

## 当前验证状态

- 已实际验证：新的 PostgreSQL 数据模型与迁移、Inbox/Outbox 基础、项目/身份隔离、Paseo 的 submit/inspect/cancel、同 key 幂等、Triage 完整 Schema、运行超时与每项目队列的本地契约。
- 未签收：真实 Git Provider 证据链、CI、测试部署、真实 Coding PR、完整恢复演练、两真实试点与 20–30 条自然反馈；验证层（ADR-0001～0012 / VER-01～28）已定稿未实施。

逐项标准与证据边界见：

- [2.0 实施计划](docs/forgeops-2.0-implementation-plan.md)
- [2.0 验收清单](docs/forgeops-2.0-acceptance-checklist.md)
- [架构决策记录（ADR）](docs/adr/README.md) — 验证层并入后的决议修订与设计定稿
- [现有实测证据](docs/evidence/v2.0/README.md)

V0.1 架构、验收与接入说明仅作历史材料，不是 2.0 的部署或接口说明：

- [V0.1 架构](forgeops-architecture-v0.1.md)
- [V0.1 验收记录](ACCEPTANCE.md)
- [V0.1 接入说明](docs/integration-guide.md)

## 本地质量检查

```bash
pnpm --filter @forgeops/paseo-runtime test

cd gateway/forgeops-gateway
mvn test
```

PostgreSQL 集成测试必须显式提供一个空的、隔离的 `forgeops_v2` 数据库；它不会访问或迁移 V0.1 数据库：

```bash
FORGEOPS_DB_URL=jdbc:postgresql://127.0.0.1:15432/forgeops_v2_probe \
FORGEOPS_DB_USER=forgeops_probe \
FORGEOPS_DB_PASSWORD='<isolated password>' \
mvn -Dtest='FeedbackWorkflowPostgresIT,IntegrationReliabilityPostgresIT,FeedbackSecurityPostgresIT' test
```

## 运行前提

Gateway 必须由部署环境提供：独立 PostgreSQL 连接、`FORGEOPS_V2_REGISTRY_PATH`、`FORGEOPS_V2_REGISTRY_WORKSPACE_ROOT`、`FORGEOPS_V2_SECURITY_TOKEN_SECRET`、`FORGEOPS_RUNTIME_URL` 和 `FORGEOPS_RUNTIME_SERVICE_TOKEN`。没有 Runtime 地址或服务凭证时 Gateway 应拒绝启动。

GitHub 交付证据默认关闭。启用后必须同时提供 `FORGEOPS_V2_GITHUB_ENABLED=true`、`FORGEOPS_V2_GITHUB_WEBHOOK_SECRET` 和最小只读权限的 `FORGEOPS_V2_GITHUB_API_TOKEN`；每个 v2 项目声明准确的 `github.repository`、`github.baseBranch`、`github.allowedMergeLogins`、唯一的 `github.requiredCheckName` 与非生产 `github.testEnvironment`。Webhook 入口为 `POST /integrations/github`，只接受 `X-Hub-Signature-256` 校验后的字节；Agent 报告的 PR 还会被 GitHub REST 查询独立比对。

Runtime 必须由部署环境提供：`FORGEOPS_RUNTIME_SERVICE_TOKEN`、`FORGEOPS_RUNTIME_DATA_FILE`、`FORGEOPS_RUNTIME_ALLOWED_ROOTS`；可配置 `FORGEOPS_RUNTIME_RUN_TIMEOUT_MS`（默认 15 分钟）和 `FORGEOPS_RUNTIME_MAX_CONCURRENT_PER_PROJECT`（默认 1）。它不会持久化 Prompt、Context 或 Agent 输出。

真实 Git/CI/测试部署接入、最小权限凭证和两个试点仓库均需 Owner 明确指定后才能进行，详见验收清单的 `S-06`、`DEL-*`、`OPS-*` 与 `VAL-*`。
