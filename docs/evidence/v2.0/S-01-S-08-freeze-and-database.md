# S 组补充证据：范围冻结、数据库与无兼容负担（S-01/02/04/05/06/08）

- 执行时间：2026-09-16/17（Asia/Shanghai）
- 绑定 commit：`390f629`、`1c8046c`（分支 `dev`）；历史项 S-03/S-07/S-09 见既有证据文件。

| ID | 结果 | 实际执行内容 |
|---|---|---|
| S-01 | PASS | 实施计划 §1/§3 固化目标与"明确不做"清单；ADR-0001～0013 落库；本轮实现严格按边界（无 Multica、无第二 Provider、无插件中心） |
| S-02 | PASS | README 将 V0.1 架构/验收/接入文档标注为"仅作历史材料"；Gateway 源码仅存 `v2` 包，无 V0.1 状态机残留 |
| S-04 | PASS | 隔离 PG 上 Flyway 从零迁移：`flyway_schema_history` 含 `V1__forgeops_v2_init` 至 `V5__verification_layer`；`ddl-auto: validate` 通过 |
| S-05 | PASS | 源码搜索无 V0.1 数据迁移/双写/回填/旧 Multica Issue 续接逻辑（`multica` 在 gateway 源码零命中；`forgeops_v2` 为唯一库名） |
| S-06 | PASS（本环境边界记录） | 本轮验证环境：隔离 PostgreSQL（Docker）、Paseo daemon 未部署（相关项 ENV_BLOCKED）、GitHub 未接入（机器 Token 缺位→autoMerge 不可用，边界已记录）、无生产环境；Multica 不是运行依赖 |
| S-08 | PASS | `@getpaseo/client` 0.8.0 锁定于 pnpm-lock（runtime 唯一运行时依赖）；daemon/Codex 版本记录沿用 S-07 证据（CLI/Server/Client 0.8.0、Codex 0.140.0） |

命令（可复核）：

```bash
docker exec <pg> psql -U forgeops -d forgeops_v2 -c "select version, script from flyway_schema_history order by installed_rank"
grep -ri multica gateway/forgeops-gateway/src || echo "no multica references"
pnpm ls @getpaseo/client --filter @forgeops/paseo-runtime
```
