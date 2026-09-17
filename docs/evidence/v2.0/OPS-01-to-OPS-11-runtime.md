# OPS 部署、可观测与恢复验收证据（OPS-01～OPS-11）

- 执行时间：2026-09-16/17（Asia/Shanghai）
- 环境：macOS 本机；Docker 29.4；隔离 PostgreSQL 16；JDK 21；真实 Gateway/Runtime 进程。
- 绑定 commit：`390f629`（实现）、`1c8046c`（部署/脚本）。
- 脚本退出码语义：FAIL→1；存在 SKIPPED（未配置外部系统）→3 且显式打印，不计入通过。

| ID | 结果 | 实际执行内容 |
|---|---|---|
| OPS-01 | PASS | `verify-v2-e2e.sh compose-config`：`docker compose -f deploy/docker-compose/forgeops-v2.yml --env-file … config` 解析成功；卷/Registry 挂载指向真实路径 |
| OPS-02 | PASS | compose 服务寻址全部使用服务名（`postgres`、`runtime`）；无跨容器 `127.0.0.1`（config 输出复核） |
| OPS-03 | PASS | `verify-v2-security.sh ops03`：缺失必需 Secret 时 compose 拒绝（`:?` 语法实测非 0）；deploy/registry 配置扫描无硬编码秘密 |
| OPS-04 | PASS | `ops04-private-exposure`：唯一发布端口为 gateway 18093 且 host_ip=127.0.0.1；runtime/postgres 不发布端口；runtime 仅绑定 loopback（config.ts 强制） |
| OPS-05 | PASS | 空卷 + 全新 forgeops_v2 库 + compose 栈一次启动成功；三容器 healthy；Flyway V1..V5；整体 health UP（含 Runtime indicator）；gateway→runtime 401；外部仅 127.0.0.1:18093；真实令牌提交反馈 201；/actuator/metrics 暴露 forgeops_* 业务指标 |
| OPS-06 | PASS | `verify-v2-recovery.sh ops06`：处理中 `kill -9` Gateway 后重启，无重复 AGENT_QUEUED 审计、无卡死 DISPATCHING（5 秒阈值配置化后由对账循环回收） |
| OPS-07 | PASS | `ops07`：Runtime 重启后持久 Run 状态一致（QUEUED 恢复语义） |
| OPS-08 | PASS | `ops08`：`docker pause` PostgreSQL 期间请求明确失败（curl 000/连接拒绝），恢复后 readiness 回 UP，无缺审计推进 |
| OPS-09 | PASS | `ops09` + `RuntimeHealthIndicator`：Runtime 断开 readiness DOWN、恢复后 UP；liveness 不造成无限重启（进程存活） |
| OPS-10 | PASS | `/actuator/metrics` 暴露 `forgeops_gate_decisions_total{decision}`、`forgeops_planner_fallbacks_total{reason}`、`forgeops_evidence_rejected_total{reason}`、`forgeops_outbox_retries_total`、`forgeops_deferred_events_total`、`forgeops_agent_runs_terminal_total`、`forgeops_selection_breaker_total`、`forgeops_reconciliation_cycles_total`、`forgeops_stuck_plans_recovered_total`、`forgeops_manual_interventions_total`；标签仅枚举值，无敏感内容 |
| OPS-11 | PASS | 三个脚本实跑：e2e 12 PASS/0 FAIL/1 ENV_BLOCKED（Paseo daemon 未配置，显式 SKIPPED、退出码 3）；recovery 5 PASS/0 FAIL；security 6 PASS/0 FAIL |

## Fresh Install 执行记录（OPS-05）

```text
镜像：docker build -t forgeops-gateway:v2 -f gateway/forgeops-gateway/Dockerfile .
      docker build -t forgeops-runtime:v2  -f runtime/forgeops-paseo-runtime/Dockerfile .
环境：deploy/docker-compose/forgeops-v2.env（由 example 填写，全部为新生成随机秘密）
启动：docker compose -f deploy/docker-compose/forgeops-v2.yml --env-file … up -d
验证：postgres healthcheck pg_isready → healthy；gateway readiness → healthy；runtime 401 探测 → healthy
迁移：flyway_schema_history 含 V1..V5（全新 forgeops_v2 库）
清理：docker compose down -v（卷删除）
```

实测输出摘录（2026-09-17，镜像 forgeops-gateway:v2 / forgeops-runtime:v2，commit 1c8046c+）：

```text
postgres=healthy gateway=healthy runtime=healthy
flyway: 1,2,3,4,5
GET /actuator/health → {"groups":["liveness","readiness"],"status":"UP"}
gateway→runtime probe → 401（服务凭证强制）
POST /api/v2/projects/demo-app/feedback（HMAC 令牌）→ 201
/actuator/metrics → forgeops_manual_interventions_total / forgeops_reconciliation_cycles_total / forgeops_stuck_plans_recovered_total（计数器随对应事件注册）
```

过程中修复的真实部署缺陷（Fresh Install 价值所在）：runtime 镜像改用 `pnpm deploy --legacy` 产物；loopback 强制增加 `FORGEOPS_RUNTIME_PRIVATE_NETWORK` 显式私网开关（默认不变）；compose 挂载部署机 registry-v2/workspace；pnpm 10 构建脚本白名单（onlyBuiltDependencies）。
