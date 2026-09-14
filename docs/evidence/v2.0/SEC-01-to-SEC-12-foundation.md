# SEC — Phase 3 身份与 Context 安全基础实测

- 执行时间：2026-09-14（Asia/Shanghai）
- 环境：本机回环隔离 PostgreSQL 16 `forgeops_v2_probe`；Gateway 测试进程随机本机端口；GraalVM Java 21.0.2。
- 执行方式：显式执行 `FeedbackSecurityPostgresIT`；令牌秘密只通过测试属性传入，未写入仓库。

已实际通过的基础项：

| 验收 ID | 实测证据 | 结果 |
|---|---|---|
| SEC-01 | v2 HTTP API 对无 Bearer Token 返回 401；HMAC-SHA-256 令牌的篡改与过期单元测试被拒绝；项目不匹配与 scope 不足的已签名令牌均返回 403 | PASS（当前 v2 API） |
| SEC-02 | 用户 A 创建反馈后，项目内用户 B 按 UUID 查询返回 403；A 查询返回 200 | PASS（当前 v2 API） |
| SEC-03 | 使用 project B 的合法写入令牌向 project A 发请求，返回 403；拒绝写入 Audit | PASS（当前 v2 API） |
| SEC-05 | 未注册 Origin 的 JSON 预检请求返回 401，且无 `Access-Control-Allow-Origin`；目前没有 Allowlist 前端配置，因此默认拒绝跨域 | PASS（默认拒绝） |
| SEC-06 | 反馈标题、描述、URL 与 Console 字段注入 `token=`、Bearer、邮箱和手机号；持久化 Feedback/Context Snapshot 均不含 token 或邮箱 | PASS（当前输入面） |
| SEC-07 | v2 API 只接受 URL、route、userAction、console、requestSummary 五个上下文字段；传入 `authorization` 字段被拒绝 | PASS（当前输入面） |
| SEC-08 | v2 API 没有截图字段或上传端点，默认不采集截图 | PASS（默认关闭） |
| SEC-11 | v2 API 仅有反馈 submit/mine/get/reopen；没有 Merge、生产部署或 Runtime 控制端点 | PASS（HTTP 面） |

尚不能签收的完整验收：

- SEC-01 的“宿主后端签发”目前是 `ProjectTokenService` 契约，尚未与真实宿主身份系统联调；
- SEC-04 需 Phase 6 的真实 Git/CI/Deploy Webhook 签名、时效和重放测试；
- SEC-05 的允许 Origin 将在 Phase 4 `ProjectCatalog` 生效后测试；
- SEC-06/07 尚未覆盖 Phase 4 SDK 浏览器采集和 Phase 5 Agent Prompt；
- SEC-09/10 依赖 Phase 5 受限 Runtime 和真实仓库凭证；
- SEC-12 需 Phase 7 汇总 Gateway、Runtime、Compose 的实际日志与证据包扫描。

因此本文件不将整个 `SEC-*` 分组标记为通过。
