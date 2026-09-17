# ForgeOps 2.0 验收证据

每个文件只记录已经实际执行的结论，不把设计、模拟或待人工操作标记为通过。

结果字段遵循 [`../../forgeops-2.0-acceptance-checklist.md`](../../forgeops-2.0-acceptance-checklist.md) 的枚举。涉及外部 Git、CI、部署、自然反馈或 Owner 审批的项目，必须另存独立、可复核的原始证据。

- [GitHub 交付证据边界（本地受控验证）](DEL-01-to-DEL-04-github-boundary.md)
- [GitHub CI 与测试部署边界（本地受控验证）](DEL-05-to-DEL-09-github-ci-deployment-boundary.md)
- [范围冻结与数据库（S-01/02/04/05/06/08）](S-01-S-08-freeze-and-database.md)
- [验证层 VER-01～VER-28（隔离 PG + 真实 Docker 沙箱）](VER-01-to-VER-28-foundation.md)
- [部署、可观测与恢复 OPS-01～OPS-11（真实进程/容器）](OPS-01-to-OPS-11-runtime.md)
- [真实 Paseo 通道验证脚本](../../../scripts/verify-v2-paseo-real.sh) — VER-05/06、AGT-04/08/09 的 daemon 通道断言（需 daemon 在线）
