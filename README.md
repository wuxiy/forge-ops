# ForgeOps 2.0（实验性）

ForgeOps 2.0 是一个受控的工程反馈闭环：反馈经 Gateway 脱敏和持久化后，由 ForgeOps 自己的状态机驱动 Paseo Runtime；Agent 的终点最多是待独立核验的 Draft PR，绝不自动 Merge、生产部署或替代人工验证。

当前实现仍处于实验验证期，尚未完成真实 Git/CI/测试部署、两个真实试点和自然反馈价值样本。因此不能作为发布级平台或生产自动化使用。

## 边界

```text
Feedback SDK / Host identity
          │
          ▼
ForgeOps Gateway ──service credential──► forgeops-paseo-runtime ──► Paseo daemon
      │                                             │
      ├─ PostgreSQL: Feedback / Cycle / Context / Run / Inbox / Outbox
      └─ Git/CI/Deploy evidence (尚未接入真实 Provider)
```

- Gateway 是状态机、重试和审计的唯一事实源；Paseo 仅是执行器。
- Runtime 只提供内部的 `submit`、`inspect`、`cancel`，默认只绑定 loopback。
- 浏览器不能传入 Agent Provider、工作目录、权限、超时或输出 Schema。
- Triage/Coding 输出必须满足代码固定的 JSON Schema；Coding 声称的 PR/Commit 不能直接推进为 `PR_READY`。
- Multica 可以单独用于人工工作管理，但不进入 2.0 主链。

## 当前验证状态

- 已实际验证：新的 PostgreSQL 数据模型与迁移、Inbox/Outbox 基础、项目/身份隔离、Paseo 的 submit/inspect/cancel、同 key 幂等、Triage 完整 Schema、运行超时与每项目队列的本地契约。
- 未签收：真实 Git Provider 证据链、CI、测试部署、真实 Coding PR、完整恢复演练、两真实试点与 20–30 条自然反馈。

逐项标准与证据边界见：

- [2.0 实施计划](docs/forgeops-2.0-implementation-plan.md)
- [2.0 验收清单](docs/forgeops-2.0-acceptance-checklist.md)
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

Runtime 必须由部署环境提供：`FORGEOPS_RUNTIME_SERVICE_TOKEN`、`FORGEOPS_RUNTIME_DATA_FILE`、`FORGEOPS_RUNTIME_ALLOWED_ROOTS`；可配置 `FORGEOPS_RUNTIME_RUN_TIMEOUT_MS`（默认 15 分钟）和 `FORGEOPS_RUNTIME_MAX_CONCURRENT_PER_PROJECT`（默认 1）。它不会持久化 Prompt、Context 或 Agent 输出。

真实 Git/CI/测试部署接入、最小权限凭证和两个试点仓库均需 Owner 明确指定后才能进行，详见验收清单的 `S-06`、`DEL-*`、`OPS-*` 与 `VAL-*`。
