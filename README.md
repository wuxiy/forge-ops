# ForgeOps

**AI Native Engineering Operations Platform — AI 原生工程智能协作平台**

打通「测试/产品反馈 → AI 分析/修复 → PR → 人工 Review → 构建发布 → 原反馈人验证 → 闭环」。

- 架构方案：[forgeops-architecture-v0.1.md](./forgeops-architecture-v0.1.md)
- 验收清单与验收记录：[ACCEPTANCE.md](./ACCEPTANCE.md)
- 接入指南：[docs/integration-guide.md](./docs/integration-guide.md)

## 仓库结构（§21）

```
forgeops/
├── gateway/forgeops-gateway/   # ForgeOps Gateway（Spring Boot 4 + PostgreSQL）
├── sdk/forgeops-feedback-vue/  # ForgeOps Feedback SDK（Vue 3 插件）
├── examples/demo-app/          # 试点：Vue + Spring Boot（含预埋 Bug）
│   ├── web/
│   └── api/
├── registry/projects/          # Project Registry（新项目接入 = 加一份 YAML）
├── schemas/                    # feedback / context-pack JSON Schema
├── multica/                    # Triage / Coding Skill 与 Issue 模板
├── policy/                     # Agent 策略 + PII 策略
├── deploy/                     # docker-compose + nginx
└── docs/
```

## 本地开发运行

```bash
# 1. 前端依赖
pnpm install

# 2. demo 后端（:8081）
cd examples/demo-app/api && mvn spring-boot:run

# 3. demo 前端（:5173）
cd examples/demo-app/web && pnpm dev

# 4. ForgeOps Gateway（:8080，需 DB + Multica 环境变量，见 application.yml）
cd gateway/forgeops-gateway
FORGEOPS_MULTICA_TOKEN=mul_xxx FORGEOPS_MULTICA_WORKSPACE_ID=<uuid> mvn spring-boot:run
```

## 核心闭环

```
反馈(SDK) → Gateway(Intake/ContextPack/PII) → Multica Issue(Triage Agent)
  → Coding Agent(feature/agent/FB-xxxx 分支+测试+Draft PR)
  → 人工 Review/Merge → CI(git/ci/deployment 回调) → 测试环境发布
  → 原反馈人验证(通过=Done / 仍有问题=Reopen 原 Issue)
```

Agent 自动化终点 = Draft PR；Merge 与发布永远人工（Human Gate）。
