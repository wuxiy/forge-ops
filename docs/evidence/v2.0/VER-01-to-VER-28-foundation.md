# VER 验证层验收证据（VER-01～VER-28）

- 执行时间：2026-09-16/17（Asia/Shanghai）
- 环境：macOS 本机；隔离 PostgreSQL 16（Docker，127.0.0.1:55432，`forgeops_v2` 库）；真实 Docker 29.4 一次性验证栈；JDK 21（GraalVM）；Maven 3.6.3；Node/pnpm。
- 绑定 commit：实现 `390f629`，部署/脚本 `1c8046c`（分支 `dev`）。
- 统一入口：`mvn verify`（surefire 495 + failsafe 31，全部执行、0 跳过）；`pnpm -r typecheck && pnpm -r test`；`scripts/verify-v2-e2e.sh`（12 PASS / 0 FAIL / 1 ENV_BLOCKED 显式跳过）。
- 证据原则：本机可真实运行的部分（隔离 PG、真实 Docker 沙箱、真实进程边界）以实跑为准；需要真实 GitHub/Paseo daemon 的条目标 `ENV_BLOCKED`，不以本地构造事件签收。

| ID | 结果 | 实际执行内容 |
|---|---|---|
| VER-01 | PASS | `FeedbackStateTest` 441 条迁移矩阵（含 PR_READY→VERIFY_RUNNING→GATE_PASS→BUILD_RUNNING、VERIFY_FAILED 两条恢复边）；`VerificationLayerPostgresIT.ver01*`：真实 PG 上门禁前 Merge 事件被拒（DEFERRED，状态不推进）、mapped check 达 GATE_PASS、CHECK/EXECUTION/BUILD/DEPLOY 失败状态分离 |
| VER-02 | PASS | `ver02newPushExpiresOpenPlans…`：synchronize 新 head 使 open plan 全部 EXPIRED 并留审计；旧 head Check 事件 EVIDENCE_REJECTED(STALE_HEAD_SHA)；状态不倒退 |
| VER-03 | PASS | `VerificationGateTest.ver03…`：POST_MERGE_CHECK 来源事实不能作为 PR 阶段证据（类别缺失→BLOCK）；Applier 按反馈状态区分两阶段来源 |
| VER-04 | PASS | `AgentContractsVerificationSchemaTest`（7 用例）：合法/缺字段/额外字段/非法枚举/重复类别/脱敏；`parseVerificationPlan` 拒绝全部非法输出 |
| VER-05 | ENV_BLOCKED | 需要真实 Paseo 通道上以同一 Verification key 并发提交 ≥10 次；本机无 daemon（e2e 脚本显式 SKIPPED）。幂等机制由 runtime 测试（含重启）与存储唯一约束覆盖，但不以本地替代签收 |
| VER-06 | ENV_BLOCKED | 同上：真实通道上的超时/取消/daemon 中断恢复未发生 |
| VER-07 | PASS | runtime `service.test.mjs` VER-07 用例：VERIFICATION/FAILURE_ANALYSIS 仅接受 taskRoots 内专用子目录，仓库 worktree 与裸根被 400 拒绝；Gateway 侧 planner 输入仅含白名单字段 |
| VER-08 | PASS | `VerificationGateTest.ver08…`：LLM 判定字段在 EvidenceFact 结构上不存在；失败事实存在时 Gate 恒 BLOCK |
| VER-09 | ENV_BLOCKED | 真实试点仓库 CI 未接入；本地已证：未映射 Check 名被拒（UNMAPPED_CHECK，`ver01verifyFailed` 链路）+ requiredCheckName+PR+SHA 精确匹配 |
| VER-10 | PASS | `DockerVerificationExecutorIT`（真实 Docker）：非白名单镜像拒绝；栈内控制面网络/宿主路径/docker.sock/只读 rootfs 全部不可达 |
| VER-11 | PASS | `ver01verifyFailed`（VERIFY_FAILED 可重试/授权转 Coding）+ `reconcileStuckPlans` 恢复卡死 plan；无状态倒退 |
| VER-12 | PASS | `ver12…`：同输入 100 次重放决策恒定；策略收紧前后差异完全由 CATEGORY_MISSING 解释 |
| VER-13 | PASS | WARN（CATEGORY_SKIPPED）不满足 autoMergeEligible；缺证据/失败/SHA 过期均 BLOCK；`attemptMachineMerge` 的 SHA_MISMATCH/POLICY_DISABLED 拒绝路径留审计 |
| VER-14 | ENV_BLOCKED | 需 ≥3 个真实 PR 人工 Merge + 独立复核后开启 autoMerge；无真实 GitHub 环境 |
| VER-15 | ENV_BLOCKED | 需真实机器身份 Merge 演练；实现与负向门禁已落地（GitHubHttpMergeClient + forgeops-machine 身份分离） |
| VER-16 | PASS | `ver16…`：plan/run/evidence/delivery 全部外键锚定同一 Cycle 与同一 PR head SHA |
| VER-17 | PASS | `ver17…`：过期证据 purge 后 payload 置空、摘要行与审计保留 |
| VER-18 | PASS | `VerificationGraphServiceTest.ver18…`：FILE→MODULE→CAPABILITY→TEST_SUITE 闭包；未命中 diff 影响集为空（不静默通过） |
| VER-19 | PASS | `ver19…`：LLM 边只扩张闭包并留痕；既有 STATIC/HUMAN 可达性不可被移除 |
| VER-20 | PASS | `ver20…`：图谱缺失/基线过期（天数/合并滞后）三种情形回退全量并给出原因 |
| VER-21 | PASS | `ver21…`：同项目任务串行、全局并发=1 排队不抢占；栈销毁后无 forgeops-verify-* 网络残留 |
| VER-22 | PASS | `ver22…`：相同种子+任务摘要一致；新种子随任务生效 |
| VER-23 | PASS | 出网/挂载/镜像白名单拒绝（ver10/ver23 用例）；栈内 mock 依赖互通而外部不可达（nc mock + wget 探测） |
| VER-24 | PASS | `ver24…`：加法语义下必需类别全量执行，planner 的 skip 建议被忽略并审计 LLM_SHRINK_IGNORED |
| VER-25 | PASS | `ver25…`：夜间失败在 SUBTRACTIVE PASS 存在时打开断路器并审计；无复核+护栏时拒绝重启用；Owner 复核+连续召回后允许 |
| VER-26 | ENV_BLOCKED | 真实 scheduled workflow 未运行；Inbox 侧 WORKFLOW_RUN 路由已实跑（签名/幂等纪律复用 SEC-04 证据） |
| VER-27 | PASS | planner 输入白名单（仅 prUrl/headSha/changedFiles/requiredCategories/mode，服务端构造）；runtime 仅绑定 loopback；证据文本过共享脱敏链 |
| VER-28 | PASS | `ver28plannerTimeout…`/`ver28retriesExhausted…`：超时→FALLBACK(PLANNER_TIMEOUT)、重试耗尽→FALLBACK(PLANNER_RETRIES_EXHAUSTED)；同树重投复用既有 plan 不重复计费；预算超限路径实现于 `onDeliveryEvidenceVerified` |

限制说明：
1. VER-05/06/09/14/15/26 六项依赖真实 Paseo daemon / 真实试点 GitHub，本环境未配置，全部 ENV_BLOCKED；对应实现与本地边界测试已存在。
2. `attemptMachineMerge` 的真实 Merge 调用未发生（无 GitHub Token）；负向四类与审计已验证。
