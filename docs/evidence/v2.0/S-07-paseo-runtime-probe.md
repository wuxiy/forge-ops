# S-07 Paseo 技术探针

- 执行时间：2026-09-14（Asia/Shanghai）
- 环境：独立临时 Paseo home 与工作目录；仅监听 `127.0.0.1:17677`；Relay、Web UI、MCP 均关闭；未使用项目目录或用户现有 Paseo 配置。
- 版本：Paseo CLI / Server / Client 0.8.0；Codex CLI 0.140.0；provider 为 `codex/gpt-5.5`。

| 契约 | 实测结果 | 结论 |
|---|---|---|
| Daemon 启动与 provider 检测 | daemon 真实监听；Codex 诊断为 Ready | PASS |
| 结构化输出约束 | 后台 + `output-schema` 被 API 拒绝；同步等待任务可执行 | PASS；适配器不能把此组合当作异步接口 |
| 真实 coding task | agent 在隔离工作目录内创建精确文本文件，并返回受 schema 限制的 JSON | PASS |
| 默认权限 | 文件变更先进入 `CodexFileChange` 待审批；仅批准该次变更后完成 | PASS |
| 查询 | `inspect` 返回 provider、model、cwd、状态和 usage | PASS |
| 取消 | 待审批任务调用 stop 后不生成文件，待审批项被清除 | PASS |
| daemon 断连与恢复 | 停止监听后端口不可达；以同一 home 重启后，两条 agent 记录和 idle 状态均可查询 | PASS |

限制说明：Paseo 的 `stop` 将该 provider 的任务展示为 `idle`，而非单独的 `cancelled` 状态。因此 ForgeOps 必须在自己的 Run 状态机和审计记录中保存“取消请求已被 Runtime 接受”的事实，不能仅用 provider 最终状态推断取消语义。
