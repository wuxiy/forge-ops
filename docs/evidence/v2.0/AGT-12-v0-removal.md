# AGT-12 — V0.1 主链删除（静态）

- 执行时间：2026-09-14（Asia/Shanghai）

已删除 Gateway 内不属于 2.0 的 Multica 客户端、Issue 编排、Poller、旧回调、旧领域模型及对应测试。`GatewayApplication` 继续只扫描 `com.company.forgeops.v2`。

实际检查：

- 对 Gateway `src/main` 与 `src/test` 搜索旧路径/字段，无命中；
- 删除后执行 `mvn test`，336/336 通过。

这只是源代码清理与编译证明。Gateway 与 Runtime 尚未组成真实业务主链，AGT-12 保持待最终运行验收，不能据此签收整个 `AGT-*` 分组。
