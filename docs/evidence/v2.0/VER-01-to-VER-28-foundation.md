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
| VER-05 | PASS | 2026-09-17 真实通道复验：本机隔离 Paseo daemon 0.8.0（Codex provider）+ `scripts/verify-v2-paseo-real.sh`：同一 VERIFICATION idempotencyKey 10 次并发提交全部返回同一 providerRunId（如 7b5e94b1-…），daemon 侧仅一个 agent 实例 |
| VER-06 | PASS | 真实通道上：人工取消（daemon 确认停止→CANCELLED）；超时路径与取消同源（provider 确认；deadline 内完成的任务按 SUCCEEDED 收尾——竞态语义修复）；daemon 中断期间 inspect 1 秒内返回本地快照不挂起，daemon 重启后单实例状态一致（无重复创建） |
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
1. ~~VER-05/06~~（2026-09-17 已在真实 daemon 通道转 PASS，见上）；VER-09/14/15/26 四项依赖真实试点 GitHub / scheduled workflow，本环境未配置，仍为 ENV_BLOCKED；对应实现与本地边界测试已存在。
2. 真实通道验证暴露并修复了三个实现缺陷（均已补回归测试，runtime 8/8）：
   a. provider 会话启动耗时数十秒，原先被 5 秒连接 guard 误杀（guard 现仅约束连接）；
   b. 验证类角色的非 git 任务目录与 `worktree: branch-off` 冲突（验证角色不再请求 worktree，符合 VER-07 只读语义）；
   c. daemon 不按 clientMessageId 去重——重投改为按携带 idempotencyKey 的 title 先找回既有 agent，避免第二个 Provider Run（找回式幂等）；另修复 agent 被外部删除时的永久 RUNNING（→ FAILED/PASEO_AGENT_MISSING）与 deadline 竞态丢弃 provider 终态两个缺口。
3. 真实 Triage 全链（gateway→runtime→daemon→Codex）：自然反馈 554965b7 提交后经状态机/Outbox 派发，真实 Codex 返回完整合法 Triage Schema（NO_CODE_REQUIRED），反馈进入 WAITING_VERIFY；agent_run attempt=1/SUCCEEDED/provider_run_id=e639c018-…。
2. `attemptMachineMerge` 的真实 Merge 调用未发生（无 GitHub Token）；负向四类与审计已验证。
