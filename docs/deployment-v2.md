# ForgeOps 2.0 部署指南（落地版）

> 适用版本：dev@9a73163 及之后；对应验收状态见[验收清单](./forgeops-2.0-acceptance-checklist.md)（106 PASS / 29 ENV_BLOCKED / 4 MANUAL_PENDING）。
>
> **可部署性结论**：可以部署为**实验验证期平台**（单机 Docker 私网部署）。Fresh Install（OPS-05）已在真实环境实跑通过：三容器 healthy、Flyway V1–V5 迁移、真实令牌提交反馈 201。已知边界：GitHub 交付链默认关闭（未配置 Token 时 Draft PR/CI/部署闭环不可用，Webhook 返回 503）；不得承载生产流量或接入生产凭证。

## 1. 部署形态与前置条件

```text
┌─ Docker 主机（私网/回环，不暴露公网） ────────────────────────────┐
│  forgeops-internal 网络                                            │
│   ├─ postgres:16-alpine      （卷 forgeops-v2-pgdata）             │
│   ├─ forgeops-gateway:v2     （127.0.0.1:18093 → 8080）            │
│   └─ forgeops-runtime:v2     （不发布端口，仅内网被 Gateway 调用）   │
│        └─ ws://<paseo主机>:6767/ws → Paseo daemon（+ Codex CLI）   │
└───────────────────────────────────────────────────────────────────┘
```

前置条件清单：

| 项 | 要求 |
|---|---|
| Docker + Compose | ≥ 24 / v2（本验证环境为 Docker 29.4） |
| 构建机 | JDK 21、Maven 3.6+、Node 22+、pnpm 10.33（`packageManager` 已锁定） |
| Paseo daemon | 0.8.0；主机上安装 Codex CLI（本验证环境 codex-cli 0.148.0，provider `codex/gpt-5.5`） |
| 网络 | daemon 仅需对 Runtime 主机可达；Gateway/Runtime 不发布公网端口 |
| 凭证 | 全部秘密由部署环境提供（见 §3）；缺失必需项时 compose 显式失败，无默认秘密 |

## 2. 构建镜像（仓库根目录）

```bash
docker build -t forgeops-gateway:v2 -f gateway/forgeops-gateway/Dockerfile .
docker build -t forgeops-runtime:v2  -f runtime/forgeops-paseo-runtime/Dockerfile .
```

- Gateway 镜像内含 curl + git（健康检查与每 Coding Run 的 git worktree 创建需要）。
- Runtime 镜像由 `pnpm deploy --legacy` 产出生产依赖（`@getpaseo/client` 0.8.0 锁定于 lockfile）。

## 3. 准备部署目录与环境变量

部署目录约定（相对 `deploy/docker-compose/`，均被 .gitignore 排除，由部署方提供）：

```bash
cd deploy/docker-compose
cp forgeops-v2.env.example forgeops-v2.env
# 逐项填写（全部为必填或显式默认）：
#   FORGEOPS_DB_USER / FORGEOPS_DB_PASSWORD / FORGEOPS_DB_NAME=forgeops_v2
#   FORGEOPS_V2_SECURITY_TOKEN_SECRET      # 浏览器短期身份令牌 HMAC 秘密
#   FORGEOPS_RUNTIME_SERVICE_TOKEN         # Gateway→Runtime Bearer
#   FORGEOPS_PASEO_URL                     # ws://host.docker.internal:6767/ws（同机 daemon）
# 可选（GitHub 交付链，默认关闭）：
#   FORGEOPS_V2_GITHUB_ENABLED=true + WEBHOOK_SECRET + API_TOKEN(+MACHINE_MERGE_TOKEN)
```

试点项目注册（Runtime 据此解析工作区与门禁策略）：

```bash
mkdir -p registry-v2 workspace/<repo>
# 1) workspace/<repo>：试点仓库的 git 克隆（base branch 存在）
# 2) registry-v2/<project>.yaml：从 registry/v2/projects/demo-app.yaml.example 复制并填写
#    （id/repositoryRoot/browserOrigins/github.*/qualityPolicy.categoryMappings）
```

## 4. 启动与健康验证

```bash
docker compose -f deploy/docker-compose/forgeops-v2.yml --env-file forgeops-v2.env up -d

# 健康检查（三容器应转 healthy；Gateway 首次启动含 Flyway V1–V5 迁移，start_period 60s）
docker compose -f deploy/docker-compose/forgeops-v2.yml ps
curl -s http://127.0.0.1:18093/actuator/health          # 期望 {"status":"UP"}
curl -s http://127.0.0.1:18093/actuator/health/readiness
```

浏览器面 API 旅程（宿主后端用 `FORGEOPS_V2_SECURITY_TOKEN_SECRET` 签发短期令牌，格式
`base64url("v1|subject|projectId|scopes|expiry") + "." + base64url(HMAC-SHA256)`，SDK 侧见
`sdk/forgeops-feedback-core`）：

```bash
TOKEN=<签发令牌>
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"…","description":"…","browserContext":{"url":"https://<试点Origin>/…"}}' \
  http://127.0.0.1:18093/api/v2/projects/<projectId>/feedback     # 期望 201
curl -H "Authorization: Bearer $TOKEN" \
  http://127.0.0.1:18093/api/v2/projects/<projectId>/feedback/mine # 期望 200
```

提交后由 Reconciler（默认 5 秒周期）自动派发 Triage 到 Runtime → Paseo daemon → Codex，
结果按代码固定 Schema 校验后推进状态机（无 GitHub 时止步于 Triage 决策/NEEDS_INPUT/NO_CODE_REQUIRED）。

## 5. Paseo daemon 部署（同机示例，已实测）

```bash
mkdir -p ~/paseo-deploy && cd ~/paseo-deploy
npm init -y >/dev/null && npm install @getpaseo/cli@0.8.0
./node_modules/.bin/paseo daemon start \
  --port 6767 --home ~/paseo-deploy/paseo-home \
  --no-relay --no-mcp --no-web-ui
./node_modules/.bin/paseo status   # Codex 应为 available (daemon)
```

- daemon 必须能发现 `codex` 可执行文件（PATH 中）。
- 版本升级（daemon/Codex/client 任一）后必须重跑 `scripts/verify-v2-paseo-real.sh` 契约验证（S-08 纪律）。

## 6. 部署后验证（脚本矩阵）

| 脚本 | 覆盖 | 退出码语义 |
|---|---|---|
| `scripts/verify-v2-e2e.sh` | mvn verify 全量 + pnpm + compose 校验 + 真实进程 API 旅程 | FAIL→1；SKIPPED→3（显式不计入通过） |
| `scripts/verify-v2-recovery.sh` | PG 中断、Gateway kill -9、Runtime 重启 | 同上 |
| `scripts/verify-v2-security.sh` | 无默认秘密、Webhook 签名/重放、日志无秘密、私网暴露 | 同上 |
| `scripts/verify-v2-paseo-real.sh` | 真实 daemon 通道：同 key 并发幂等、取消、断连恢复 | 无 daemon→3 |

## 7. 运维

**观测**：`/actuator/health{,/readiness,/liveness}`；`/actuator/metrics` 暴露
`forgeops_gate_decisions_total{decision}`、`forgeops_planner_fallbacks_total{reason}`、
`forgeops_evidence_rejected_total{reason}`、`forgeops_outbox_retries_total`、
`forgeops_deferred_events_total`、`forgeops_agent_runs_terminal_total`、
`forgeops_selection_breaker_total`、`forgeops_manual_interventions_total` 等业务指标（标签仅枚举值，无敏感内容）。

**恢复语义**（均已实跑验证）：
- Gateway `kill -9` 重启：Outbox 无重复外部动作、无卡死 DISPATCHING（对账阈值
  `forgeops.v2.reconciliation.abandoned-threshold-seconds`，默认 300s）。
- PostgreSQL 中断：请求明确失败，恢复后 readiness 回 UP。
- Runtime/daemon 重启：Run 状态持久一致；daemon 离线期间提交排队（PASEO_UNAVAILABLE），恢复后续投且按幂等 key 找回既有 agent，不重复创建。

**证据保留**：验证证据按 `qualityPolicy.evidenceRetentionDays` 过期清理（payload 清除、摘要行与审计永久保留）。

## 8. 升级与回滚

1. 新版本镜像使用新 tag（如 `forgeops-gateway:v2-<sha>`）并在 compose 中替换；先 `mvn verify` + 脚本矩阵全绿再发布。
2. 数据库迁移为 Flyway 前向（V1→V5+）；**无降级迁移**，升级前备份卷：
   `docker run --rm -v forgeops-v2_<project>_forgeops-v2-pgdata:/data -v $PWD:/backup alpine tar czf /backup/pgdata.tgz /data`
3. 回滚 = 回滚镜像 tag + 恢复卷备份；不支持跨迁移版本回滚应用。

## 9. 安全清单（部署时逐项确认）

- [ ] `forgeops-v2.env` 权限 600，未入库（.gitignore 已排除）
- [ ] Gateway 仅绑 127.0.0.1:18093（对外经反向代理/Tailscale，见既有 nginx 配置可复用）
- [ ] Runtime 不发布端口；Paseo daemon 仅私网可达
- [ ] 未配置 `FORGEOPS_V2_GITHUB_*` 前不创建 GitHub Webhook
- [ ] 机器 Merge Token（如启用 autoMerge）与人工 Token 分离，账号可区分
- [ ] 部署后跑一遍 `scripts/verify-v2-security.sh`
