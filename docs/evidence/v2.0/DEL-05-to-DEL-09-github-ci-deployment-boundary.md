# DEL — GitHub CI 与测试部署边界（本地受控验证）

- 执行时间：2026-09-15（Asia/Shanghai）
- 环境：隔离 PostgreSQL 16 `forgeops_v2_v4b_probe`；GitHub Webhook 事实在测试中直接构造；没有真实 GitHub Delivery、CI 运行或测试环境。

本次实现的不可放宽约束：

1. 每个项目 Registry 必须明确声明唯一 `github.requiredCheckName` 和非生产 `github.testEnvironment`。任意其他 Check Run、生产环境或未注册仓库均不能推进状态。
2. `check_run` 必须携带 Check ID、配置的 Check 名、关联 PR number、head SHA、完成状态和 conclusion。CI 事件仅匹配已经独立核验过的同一 repo/PR/head SHA。
3. `deployment_status` 必须携带 deployment/status ID、由受控部署流程写入的 `deployment.payload.forgeopsPullRequestNo`、head SHA 和精确测试环境。该字段只用于关联，不保留 deployment payload 原文。
4. CI 失败只会得到 `BUILD_FAILED`；同一绑定的后续成功 Check Run 才能恢复到 `DEPLOY_RUNNING`。测试部署失败同理进入 `DEPLOY_FAILED`，后续成功事件才进入 `WAITING_VERIFY`。
5. Inbox 的 `externalEventId` 仍是去重键；前置条件未满足的 CI/部署事实为 `DEFERRED`，当前 Cycle 已过期的事实拒绝，均不能倒退状态。

本次实际结果：

| 范围 | 证据 | 结果 |
|---|---|---|
| Webhook 最小化 | `GitHubWebhookNormalizerTest`：Check Run 与 Deployment 仅留下关联字段、摘要和状态；任意 output/payload/description 原文不保存 | PASS（单元） |
| CI 目标绑定 | `GitHubIntegrationApplierTest`：非配置 Check 拒绝；配置 Check 且 repo/PR/SHA 精确匹配才调用 Build 状态迁移 | PASS（单元） |
| CI/部署重试状态 | `DeliveryEvidencePostgresIT`：`BUILD_RUNNING → BUILD_FAILED → DEPLOY_RUNNING → DEPLOY_FAILED → WAITING_VERIFY`；错误 SHA 为 `DEFERRED` | PASS（隔离 PostgreSQL） |
| 项目配置收紧 | `ProjectCatalogTest`：`requiredCheckName` 是 Registry 必填字段 | PASS（单元） |

本次没有签收：

- `DEL-04` 至 `DEL-09` 的真实验收。构造 Inbox 事实、HMAC 向量与测试替身均不能替代 GitHub 实际 Delivery、实际 CI、真实测试环境或 Owner 的人工 Merge。
- `DEL-07` 所要求的测试环境“运行中的版本/commit 独立查询”尚未接入；当前受控路径只能验证签名 Deployment 事实与配置的 PR/SHA/环境一致，不能证明环境实际服务的版本。
- 真实试点接入前，Owner 必须指定仓库、`requiredCheckName`、测试环境、部署流程如何写入 `forgeopsPullRequestNo`、测试环境版本查询接口和最小权限凭证；之后须按验收清单实际重跑。
