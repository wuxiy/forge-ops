# EDGE — Phase 4 Registry 与 SDK 基础实测

- 执行时间：2026-09-14（Asia/Shanghai）
- 环境：Gateway 使用本机回环隔离 PostgreSQL 16；SDK 使用 Node 24.12.0 与 pnpm 10.33.0。

已实际通过的基础项：

| 验收 ID | 实测证据 | 结果 |
|---|---|---|
| EDGE-01 | `ProjectCatalogTest` 构造未知字段、放宽 Human Gate 的无效 YAML；均被拒绝。启动配置要求 Registry 目录和 workspace root，目录为空或启用项目无效会失败 | PASS（代码与单测） |
| EDGE-02 | 先加载有效 Catalog，再写入无效策略并刷新；刷新抛错，原 `pilot` Catalog 仍可查询 | PASS |
| EDGE-03 | Project YAML 中 `autoMerge: true` 或 `productionDeploy: true` 被拒绝；路径必须存在于 repositoryRoot 内 | PASS（当前全局 Human Gate） |
| EDGE-05 / EDGE-06 / EDGE-07 | Core 实测重复 start 不重复包装 fetch；失败请求只记录一次；stop 恢复原始 fetch 和 console.error | PASS（Core 生命周期范围） |
| EDGE-08 | pnpm workspace 只保留 Core/DOM；Vue/Vue2/React wrapper 与 Vue2 demo 已移出 workspace，并在 `sdk/ARCHIVED-V0.1-PACKAGES.md` 标为历史材料 | PASS |
| EDGE-09 | SDK 只在请求时调用宿主 `getToken`，没有匿名姓名或内嵌 Token 回退；Gateway 对缺失、篡改、过期 Token 拒绝 | PASS（契约与 Gateway） |
| EDGE-10 | SDK 只提交 URL、route、console、requestSummary；Gateway `ContextPreparation` 对最终 Snapshot 二次脱敏并拒绝不在白名单的字段 | PASS（当前字段面） |

跨层执行记录：

- `pnpm --filter @forgeops/feedback-core test`：1/1 通过；
- `pnpm -r typecheck`：Core、DOM、Vue3 demo、React demo、tracing 包通过；Vue2 已不在 2.0 workspace；
- `mvn test`：353 项通过；
- `FeedbackSecurityPostgresIT`：Registry 驱动的允许/拒绝 Origin、Token、项目与用户隔离通过。

尚不能签收：

- EDGE-04：真实日志 Adapter 在 Phase 6/7 引入后验证；
- EDGE-06 的 DOM mount/destroy 10 次浏览器旅程尚未执行；
- EDGE-11：两个 demo 只完成类型检查。它们已改为向宿主 `/api/forgeops/token` 请求短期 Token；当前没有得到两个真实试点的宿主签发端、项目 Registry 和授权环境，不能伪造浏览器提交/Mine/Reopen PASS。

因此本文件不将整个 `EDGE-*` 分组标记为通过。
