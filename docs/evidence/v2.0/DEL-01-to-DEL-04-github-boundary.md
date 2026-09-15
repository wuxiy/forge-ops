# DEL — GitHub 交付证据边界（本地受控验证）

- 执行时间：2026-09-15（Asia/Shanghai）
- 环境：隔离 PostgreSQL 16 `forgeops_v2_probe`；GitHub 查询使用测试替身；没有配置真实仓库、真实 Webhook、GitHub Token、CI 或测试部署。

本次实际验证的代码边界：

1. 项目 Registry 必须显式声明 GitHub 仓库、base branch、允许 Merge 的 GitHub 登录名和测试环境；不能从 Agent 的 PR URL 或浏览器请求推断。
2. GitHub Webhook 以原始请求字节校验 `X-Hub-Signature-256`，仅保存字段白名单与 `payloadSha256`，不保存 PR 正文等原始内容；`X-GitHub-Delivery` 进入 Inbox 去重键。
3. Coding Agent 的 PR 声明只创建 `delivery_evidence(PENDING)`；独立 GitHub 查询必须同时匹配 repository、PR number、URL、开放状态、Draft、base、head branch 和 head SHA，才允许 `CODE_RUNNING → PR_READY`。
4. 已签名的 Merge 事实还必须匹配上述核验证据、当前 SHA 和项目 `allowedMergeLogins`，才允许 `PR_READY → BUILD_RUNNING`。未授权 actor 拒绝；乱序、未核验或不同提交的事件不推进状态。

实际执行结果：

| 范围 | 证据 | 结果 |
|---|---|---|
| 签名与最小化 | GitHub 发布的 HMAC-SHA256 向量、篡改字节、错误算法、禁用入口、未知/缺字段事件 | PASS（单元/控制器契约） |
| PR 独立核验 | 正确的开放 Draft PR、错误 branch/SHA/non-draft/closed 状态 | PASS（匹配器契约） |
| 数据库状态链 | PostgreSQL 迁移 `V1–V4`；Coding 声明后仍为 `CODE_RUNNING`，替身返回完全匹配 PR 后为 `PR_READY`，再由 Inbox Merge 事实变为 `BUILD_RUNNING`；同一 repo/PR 绑定到第二个 Cycle 被数据库唯一约束拒绝；过期证据被拒绝且不改变非 Coding Feedback 的状态 | PASS（隔离 DB + GitHub 查询替身） |
| 人工 Merge 门禁 | allowlisted 登录名允许；未授权登录拒绝；不同 head SHA 延后 | PASS（处理器契约） |

本次没有签收：

- `SEC-04`、`AGT-07`、`DEL-01` 至 `DEL-04` 的真实验收。测试替身、HMAC 测试向量、直接构造的 Inbox 事实均不能替代 GitHub 实际 Delivery。
- 真实 Draft PR、真实授权用户 Merge、签名 Webhook 重放、真实 GitHub REST 查询、真实 CI 与测试环境部署均未执行。
- 因此该文件只能证明当前代码的本地受控路径；真实试点必须按验收清单保留 Provider 查询结果、Webhook Delivery ID、commit、CI run、测试环境版本和 Owner 身份证据。
