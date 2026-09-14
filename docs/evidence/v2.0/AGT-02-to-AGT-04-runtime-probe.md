# AGT — Phase 5 Paseo Runtime 实测（局部）

- 执行时间：2026-09-14（Asia/Shanghai）
- Runtime：`@forgeops/paseo-runtime`，Node 24.12.0，`@getpaseo/client` 0.8.0。
- 隔离环境：loopback Runtime、独立 Paseo daemon，以及 `/private/tmp` 下临时 Git 仓库；没有访问 ForgeOps 工作目录、真实试点、Git Provider、CI 或部署环境。

已实际执行的结果：

| 验收 ID | 实测证据 | 结果 |
|---|---|---|
| AGT-02 | Runtime 仅暴露 `POST /v1/runs`、`GET /v1/runs/:idempotencyKey` 与 `POST /v1/runs/:idempotencyKey`；真实 Paseo SDK 完成 submit、inspect、cancel | PASS（Runtime 边界） |
| AGT-03 | 无 Bearer Token 请求返回 401；Runtime 配置拒绝 `0.0.0.0`，仅允许 loopback | PASS（Runtime 自身；Gateway 到 Runtime 的网络策略尚未验收） |
| AGT-04 | 同一 `idempotencyKey` 并发 10 次真实 HTTP Submit，全部返回 202、只得到 1 个 Provider Run；随后真实取消返回 200 / `CANCELLED` | PASS（单 Runtime 进程） |
| AGT-10（局部） | Paseo 为实测任务创建独立 Git worktree；原临时仓库保持 `main` 且无工作区改动。Runtime 为每个 key 请求确定性 `forgeops/v2-*` 分支 | PASS（单仓库、单次与取消任务；并发 Coding Run 尚未执行） |

自动化补充：

- `pnpm --filter @forgeops/paseo-runtime typecheck`：通过；
- `pnpm --filter @forgeops/paseo-runtime test`：2/2 通过，覆盖 Bearer 鉴权、路径隔离、10 次并发幂等、持久化重启恢复与取消；
- Runtime 对已完成的真实 Probe 读取 Paseo canonical timeline，合并 assistant 输出片段并得到规范化 JSON `{"decision":"ok"}`；输出只在受保护的 inspect 响应中返回，不写入 Runtime 本地状态文件；
- 首次执行发现并修复并发请求返回 `SUBMITTING` 且缺少 Provider Run ID 的竞态；当前同 key 请求会等待首个提交完成。

未签收的项：

- AGT-05 / AGT-06：Runtime 仅透传 `outputSchema`，尚未将 Triage/Coding 结果解析为受控状态机输入；
- AGT-07 / AGT-08（超时部分）/ AGT-09 / AGT-11：尚未实现或在真实故障环境验证；
- AGT-10 的两条 Coding Run 并发隔离、以及完整 base/commit/分支核验仍待真实试点；
- AGT-12 与 Gateway、Outbox、`AgentRun` 的主链接入尚未完成。因此本文件不将整个 `AGT-*` 分组标记为通过。
