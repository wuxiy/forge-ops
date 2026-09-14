# S-03 基线记录

- 执行时间：2026-09-14（Asia/Shanghai）
- 执行环境：macOS arm64，GraalVM Community Java 21.0.2，Maven 3.6.3，Node 24.12.0，pnpm 10.33.0
- 代码基线：`6882468`

| 检查 | 实际结果 | 结论 |
|---|---|---|
| `gateway/forgeops-gateway: mvn verify` | 17 tests，0 failures，0 errors，0 skipped | PASS |
| `sdk/forgeops-spring-boot-starter: mvn verify` | 3 tests，0 failures，0 errors，0 skipped | PASS |
| `pnpm -r typecheck` | 命令退出 0；`demo-vue2` 脚本只输出 `skip typecheck` | SKIPPED（不能计为类型检查 PASS） |

此前 Gateway 的两项 Mockito 测试受 JVM 自附加限制报错。本阶段通过测试资源 `mock-maker-subclass` 使这些接口 mock 不再依赖该限制；随后重新执行的 17 项测试全部通过。
