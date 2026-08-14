# ForgeOps 项目架构方案

> 项目：ForgeOps  
> 全称：ForgeOps - AI Native Engineering Operations Platform  
> 版本：V0.1 Architecture Baseline  
> 更新日期：2026-08-15  
> 适用阶段：公司内部测试环境试运行  
> 首批目标项目：Vue + Spring Boot 业务系统  
> 核心目标：打通“反馈 → AI 分析/修复 → PR → 人工 Review → 构建发布 → 原反馈人验证 → 闭环”

---

## 1. 项目背景

当前测试环境主要由测试人员和产品人员使用。迭代版本测试过程中，问题与优化建议通常经过以下链路：

```text
测试 / 产品发现问题
        ↓
线上文档记录
        ↓
转发给开发人员
        ↓
开发人员重现问题
        ↓
调查日志 / 请求 / 代码
        ↓
人工归纳问题上下文
        ↓
交给 AI 分析 / 修改
        ↓
人工 Review
        ↓
提交代码
        ↓
构建并发布测试环境
        ↓
重新验证
        ↓
人工告知测试 / 产品完成
```

该流程当前存在四类明显成本：

1. **上下文重复整理**：问题描述、版本、页面、接口、日志、代码信息需要开发人员二次收集。
2. **信息传递损耗**：线上文档、群聊、口头沟通之间容易丢失细节。
3. **开发人员被动中转**：大量工作不是写代码，而是重现、查日志、整理问题给 AI。
4. **闭环不完整**：代码发布后仍需人工找到原反馈人并通知验证，Issue 状态难以统一追踪。

因此，本项目第一阶段不追求“无人值守自动修复”，而是先将现有人工流程转化为一条**可追踪、上下文自动化、Agent 可参与、人类关键节点审核**的 Engineering Workflow。

---

## 2. 项目定位

项目正式命名为：

```text
ForgeOps
```

英文全称：

```text
ForgeOps - AI Native Engineering Operations Platform
```

中文定位：

```text
AI 原生工程智能协作平台
```

核心定位：

> 面向公司内部研发流程的 AI Native Engineering Operations Platform，使测试、产品、开发、AI Coding Agent、Git 与 CI/CD 形成统一的问题处理闭环。

ForgeOps 的命名强调“工程闭环”而不是某一种 AI 技术：Feedback、Issue、Agent、Code、Review、Build、Deploy、Verify 都属于 ForgeOps 的统一工程操作链路。项目名不绑定 Claude Code、Codex、Multica 等具体实现，便于后续替换底层组件。

V0.1 的核心不是重新开发一个 AI Agent，而是把以下能力编排起来：

```text
Human Feedback
      ↓
Engineering Context Pack
      ↓
Multica Issue / Agent
      ↓
Claude Code / Codex
      ↓
Git PR
      ↓
Human Review
      ↓
CI/CD
      ↓
Verification
```

---

### 2.1 项目与模块命名约定

```text
ForgeOps                     项目总名
forgeops                     Git 仓库名
ForgeOps Feedback SDK        业务系统内反馈采集组件
ForgeOps Gateway             公司侧工程接入与编排服务
Project Registry             项目元数据与集成映射
ForgeOps Policy              Agent 自动化与安全策略
Engineering Context Pack     Agent 统一上下文协议
ForgeOps Edge                后续隔离网边缘接入组件
```

第三方组件保持其原始名称，不二次封装成“ForgeOps Multica”或“ForgeOps Keep”，以降低耦合并方便未来替换。

---

## 3. V0.1 建设目标

V0.1 只验证一个核心假设：

> 测试或产品人员在测试环境提交一个问题后，系统能否自动构造足够好的工程上下文，由 Agent 完成分析和代码修改，产出可 Review 的 PR，并在发布后自动回到原反馈人完成验证。

### 3.1 V0.1 必须实现

- 在现有 Vue 测试环境内提供统一“反馈”入口。
- 自动采集页面、版本、用户、浏览器、最近失败请求、Request ID / Trace ID 等上下文。
- 将反馈转化为统一的 Engineering Context Pack。
- 自动创建 Multica Issue。
- 由 Triage Agent 分析问题并补齐工程上下文。
- 由 Coding Agent 修改代码、执行测试并创建 Draft PR。
- 开发人员人工 Review，禁止 Agent 直接合并主分支。
- 复用现有 CI/CD 构建、发布测试环境。
- 发布完成后自动通知原反馈人验证。
- 支持“验证通过 / 仍有问题”两种结果。
- “仍有问题”必须 Reopen 原问题，而不是创建新的孤立问题。

### 3.2 V0.1 明确不做

- 不做生产环境自动修复。
- 不做 Agent 自动 Merge。
- 不做 Agent 自动生产发布。
- 不做完整 APM 平台。
- 不自研日志平台。
- 不重做现有 CI/CD。
- 不引入复杂多 Agent Debate。
- 不引入 HolmesGPT 作为首版核心依赖。
- 不引入 OpenHands 作为第二套 Agent Control Plane。
- Keep 不进入首版“人工反馈”主链路。
- 不先建设医院 / 政务隔离网 ForgeOps Edge。

---

## 4. 核心架构决议

### 4.1 核心组件选型

| 能力 | V0.1 方案 | 决议 |
|---|---|---|
| 人工反馈入口 | ForgeOps Feedback SDK / Vue Plugin | 自研 |
| 工程接入层 | ForgeOps Gateway | 自研，Spring Boot |
| 工程项目配置 | Project Registry | 自研，YAML + DB 缓存 |
| 上下文构建 | Context Pack Builder | 自研 |
| Agent 工作管理 | Multica | 直接采用 |
| Coding Agent | Claude Code / Codex | 直接采用 |
| Agent Runtime | Multica Daemon + 独立 Runtime Pool | 直接采用 |
| Git | 现有 Git 平台 | 复用 |
| CI/CD | 现有流水线 | 复用 |
| 自动异常治理 | Keep | V0.2+ |
| SRE 根因分析 | HolmesGPT | V0.3+ |
| Agent Framework / Sandbox 参考 | OpenHands | 不部署，仅借鉴 |

### 4.2 平台边界

```text
Keep      = Event Control Plane（V0.2+）
Multica   = Engineering Work / Agent Control Plane
HolmesGPT = SRE Investigator（V0.3+）
ForgeOps  = Project Registry + Policy + Context + Edge
```

V0.1 只启用：

```text
Feedback SDK
    +
ForgeOps Gateway
    +
Multica
    +
Claude Code / Codex
    +
Existing Git / CI/CD
```

---

## 5. V0.1 总体架构

```text
                        测试 / 产品人员
                              │
                              │ 业务页面点击「反馈」
                              ▼
                    ┌────────────────────┐
                    │ ForgeOps Feedback  │
                    │                    │
                    │ 页面信息           │
                    │ Screenshot         │
                    │ Version / Commit   │
                    │ Failed Requests    │
                    │ Request / Trace ID │
                    └─────────┬──────────┘
                              │ HTTPS
                              ▼
                 ┌───────────────────────────┐
                 │ ForgeOps Gateway          │
                 │ Spring Boot               │
                 │                           │
                 │ Feedback Intake           │
                 │ Project Registry          │
                 │ Context Pack Builder      │
                 │ Policy Engine             │
                 │ Multica Adapter           │
                 │ Git / CI Adapter          │
                 │ Verification Workflow     │
                 └────────────┬──────────────┘
                              │
                              ▼
                         ┌─────────┐
                         │ Multica │
                         │         │
                         │ Issue   │
                         │ Agent   │
                         │ Task    │
                         │ Runtime │
                         └────┬────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ Runtime Pool      │
                    │ Container / VM    │
                    │ Dedicated User    │
                    │                   │
                    │ Claude Code       │
                    │ Codex             │
                    └────────┬──────────┘
                             │
                             ▼
                            Git
                             │
                             ▼
                         Draft PR
                             │
                             ▼
                      Developer Review
                             │
                             ▼
                         Existing CI
                             │
                             ▼
                       Test Deployment
                             │
                             ▼
                   ForgeOps Gateway
                             │
                             ▼
                    原反馈人验证 / Reopen
```

---

## 6. 核心业务流程

### 6.1 人工反馈主流程

```text
SUBMITTED
   ↓
CONTEXT_BUILDING
   ↓
TRIAGING
   ↓
CODING
   ↓
PR_REVIEW
   ↓
BUILDING
   ↓
DEPLOYING
   ↓
WAITING_VERIFY
   ↓
DONE
```

异常分支：

```text
TRIAGING ─────→ NEED_INFO
CODING ───────→ AGENT_FAILED
PR_REVIEW ────→ CHANGES_REQUESTED
BUILDING ─────→ BUILD_FAILED
WAITING_VERIFY → REOPENED → TRIAGING / CODING
```

### 6.2 用户可见状态

测试和产品人员不需要看到内部几十个 Agent 状态，只保留：

```text
待处理
AI 分析中
开发处理中
待发布
待验证
已完成
需要补充
```

内部技术状态由 Gateway / Multica 维护。

---

## 7. Feedback SDK 设计

### 7.1 形态

建议做成独立 Vue Plugin：

```text
@company/forgeops-feedback-vue
```

组件内部名称统一为 **ForgeOps Feedback SDK**。

只在以下环境开启：

```text
test
uat
staging
```

默认不在生产环境启用。

### 7.2 用户交互

业务页面右下角提供统一入口：

```text
[ 反馈 ]
```

反馈类型：

```text
Bug
优化建议
需求建议
```

用户填写：

```text
问题描述
预期效果
操作步骤（可选）
补充说明（可选）
```

系统自动附带：

```text
当前 URL / Route
页面标题
Frontend Version
Frontend Commit SHA
Backend Version
当前登录用户
Browser / OS
屏幕尺寸
最近失败接口
最近 Console Error
Request ID / Trace ID
截图（可选）
提交时间
```

### 7.3 前端请求缓冲区

SDK 在内存中维护最近一段时间的请求摘要：

```text
最近 20~50 条请求
```

仅保存必要字段：

```json
{
  "method": "GET",
  "url": "/api/exam/list",
  "status": 500,
  "duration": 183,
  "requestId": "01J...",
  "traceId": "...",
  "time": "..."
}
```

禁止默认保存：

```text
Authorization
Cookie
Token
Password
完整请求 Body
完整 Response Body
患者敏感信息
身份证
手机号
病历号
```

---

## 8. Request ID / Trace ID 规范

V0.1 必须优先完成请求链路关联。

### 8.1 请求链路

```text
Browser
   ↓ X-Request-ID
Nginx / Gateway
   ↓
Spring Boot
   ↓ MDC
Service
   ↓
Log
```

### 8.2 推荐 Header

```text
X-Request-ID
traceparent（如未来全面接入 OpenTelemetry）
```

### 8.3 Spring Boot 日志字段

统一结构化输出：

```text
requestId
traceId
userId
service
uri
httpMethod
status
version
commitSha
```

最终实现：

```text
Feedback
   ↓
requestId
   ↓
直接查到对应日志
```

这是降低“开发人员手工重现 + 找日志”成本的关键基础设施。

---

## 9. ForgeOps Gateway

建议 V0.1 使用：

```text
Spring Boot 4
PostgreSQL
```

首版不引入 Kafka。

### 9.1 Gateway 职责

```text
Feedback Intake
Project Registry
Context Pack Builder
Policy Engine
Multica Adapter
Git Adapter
CI/CD Adapter
Verification Workflow
Notification
Audit
```

### 9.2 Gateway 不负责

```text
Agent 对话 UI
Agent Runtime
代码执行
完整 Issue Management
日志存储
APM
CI/CD Engine
```

这些能力分别交给 Multica、现有 Git、现有日志体系和现有 CI/CD。

### 9.3 推荐 API

```text
POST   /api/v1/feedback
GET    /api/v1/feedback/{id}
POST   /api/v1/feedback/{id}/comment
POST   /api/v1/feedback/{id}/verify
POST   /api/v1/feedback/{id}/reopen

POST   /api/v1/callback/multica
POST   /api/v1/callback/git
POST   /api/v1/callback/ci
POST   /api/v1/callback/deployment

GET    /api/v1/projects/{projectId}/config
```

---

## 10. Project Registry

Project Registry 是公司侧必须长期掌握的核心资产。

一个项目用一份配置描述：

```yaml
id: health-platform
name: 健康平台

feedback:
  enabled: true

frontend:
  framework: vue
  repo: health/data-web
  defaultBranch: develop

backend:
  framework: springboot
  repo: health/data-api
  defaultBranch: develop

runtime:
  pool: health-java-fullstack

observability:
  logs:
    type: elasticsearch
    service: health-api

multica:
  workspace: engineering
  project: health-platform
  triageAgent: health-triage
  codingAgent: health-java-fullstack

ci:
  provider: jenkins
  pipeline: health-platform-test

environments:
  test:
    frontendUrl: https://test.example.internal
    backendUrl: https://test-api.example.internal

policy:
  autoAnalyze: true
  autoCode: true
  autoPR: true
  autoMerge: false
  autoDeploy: false

security:
  piiProfile: healthcare-strict
```

### 10.1 Registry 解决的问题

```text
feedback.projectId
       ↓
哪个前端 Repo？
哪个后端 Repo？
默认分支？
日志在哪里？
哪个 Multica Project？
哪个 Agent？
哪个 Runtime Pool？
哪个 CI Pipeline？
允许 Agent 做到哪一步？
```

未来新项目接入应尽量做到：

```text
安装 Feedback SDK
+
增加 project.yaml
```

而不是修改平台核心代码。

---

## 11. Engineering Context Pack

Context Pack 是平台未来最关键的数据协议之一。

所有 Coding Agent 都不直接消费“测试人员的一句话”，而消费标准化的 Engineering Context Pack。

### 11.1 建议 Schema

```json
{
  "schemaVersion": "1.0",
  "feedback": {
    "id": "FB-1023",
    "type": "BUG",
    "title": "检查记录页面持续 loading",
    "description": "...",
    "reporter": "user-123"
  },
  "project": {
    "id": "health-platform",
    "environment": "test"
  },
  "page": {
    "url": "/patient/10001",
    "route": "patient-detail"
  },
  "frontend": {
    "version": "1.8.3",
    "commit": "a81fd21"
  },
  "backend": {
    "version": "2.5.7",
    "commit": "fa0912c"
  },
  "requests": [
    {
      "method": "GET",
      "url": "/api/examination/list",
      "status": 500,
      "requestId": "01J...",
      "traceId": "..."
    }
  ],
  "logs": [],
  "git": {
    "frontendRepo": "health/data-web",
    "backendRepo": "health/data-api"
  },
  "attachments": []
}
```

### 11.2 Context Pack Builder 职责

Context Pack Builder 尽可能自动补齐：

```text
版本
Commit SHA
Request ID
Trace ID
错误接口
关联日志
所属服务
所属 Repo
最近发布信息
相关文件
历史反馈
```

未来 Agent 成功率的核心变量，不是模型本身，而是 Context Pack 的质量。

---

## 12. Agent 设计

V0.1 不追求复杂 Squad，建议只保留两个主要角色。

### 12.1 Triage Agent

职责：

```text
识别 Bug / Optimization / Requirement
判断影响模块
根据 Request ID / Trace ID 查询日志
定位相关 Repo
定位可能代码范围
判断上下文是否充分
生成根因分析
决定是否进入 Coding
```

输出：

```text
问题分类
影响范围
根因推断
证据
相关文件
风险
建议处理方案
是否需要人工补充信息
```

### 12.2 Coding Agent

职责：

```text
读取 Context Pack
创建独立工作目录 / Worktree
修改代码
增加 / 更新测试
执行项目测试命令
生成 Commit
创建 Draft PR
生成 PR Summary
```

禁止：

```text
直接 push main/master
自动 Merge
直接发布生产
读取无关项目凭据
访问无权限 Repo
```

---

## 13. Multica 使用边界

Multica 作为：

```text
Engineering Work / Agent Control Plane
```

负责：

```text
Workspace
Project
Issue
Agent
Task
Runtime
Run History
Review Context
```

Gateway 不复制 Multica 的 Agent 工作数据，只保存业务侧映射。

推荐映射：

```text
forgeops_feedback.multica_issue_id
```

一个反馈原则上对应一个主 Multica Issue。

### 13.1 Issue 内容模板

```markdown
# Feedback

用户原始反馈……

# Environment

project: health-platform
env: test
frontend: 1.8.3 / a81fd21
backend: 2.5.7 / fa0912c

# Failed Requests

GET /api/examination/list
status: 500
requestId: 01J...

# Context Pack

...

# Acceptance Criteria

1. 页面不再持续 loading
2. 无数据场景正常显示空态
3. 原有有数据场景不受影响
```

---

## 14. Runtime Pool 安全架构

生产试运行阶段禁止：

```text
一台 Multica Server
+
一个系统用户
+
公司全部 Repo
+
所有 Git / CI / Cloud 凭据
+
Claude Code / Codex
```

推荐：

```text
                    Multica Server
                         │
             ┌───────────┴───────────┐
             │                       │
             ▼                       ▼
      Runtime Pool A           Runtime Pool B
      Test Project Group       Other Project Group
             │                       │
      Container / VM           Container / VM
      Dedicated User           Dedicated User
             │                       │
       Claude / Codex           Claude / Codex
             │                       │
       Scoped Git Token         Scoped Git Token
```

V0.1 即使只有一个项目，也建议：

```text
Multica Server
      ≠
Agent Runtime Host
```

至少使用：

```text
独立 Linux 用户
或
独立 Container / VM
```

Runtime 只挂载当前试点项目 Repo 与必要凭据。

---

## 15. Git / PR 流程

推荐：

```text
Multica Issue
    ↓
Agent Task
    ↓
feature/agent/FB-1023
    ↓
Commit
    ↓
Draft PR
    ↓
Developer Review
    ↓
Merge
```

Agent PR 自动包含：

```markdown
## 问题

## 根因

## 修改内容

## 测试结果

## 风险

## 来源
Feedback: FB-1023
Multica Issue: ENG-483
```

### 15.1 Human Gate

V0.1 强制：

```text
Agent → Draft PR → Human Review → Merge
```

不得跳过人工 Review。

---

## 16. CI/CD 集成

V0.1 完全复用现有构建发布体系。

Gateway 只接收回调：

```text
PR merged
   ↓
CI Build Started
   ↓
Build Success
   ↓
Deploy Test
   ↓
Deployment Success
   ↓
Gateway Callback
```

Gateway 更新反馈状态：

```text
PR_REVIEW
→ BUILDING
→ DEPLOYING
→ WAITING_VERIFY
```

建议 CI 回调携带：

```json
{
  "projectId": "health-platform",
  "feedbackId": "FB-1023",
  "commitSha": "...",
  "pipelineId": "12831",
  "environment": "test",
  "version": "1.8.4",
  "status": "SUCCESS"
}
```

---

## 17. 验证闭环

部署测试环境完成后，平台自动通知原反馈人：

```text
FB-1023 已修复并部署到测试环境。
版本：1.8.4
请进行验证。
```

反馈详情页提供：

```text
[ 验证通过 ]    [ 仍有问题 ]
```

### 17.1 验证通过

```text
feedback.status = DONE
Multica Issue = Done
```

### 17.2 仍有问题

用户必须可以补充：

```text
验证说明
新截图
新请求上下文
```

然后：

```text
Feedback REOPENED
       ↓
追加 Context Pack
       ↓
Multica Issue Reopen / Comment
       ↓
Agent Resume
```

禁止重新创建孤立 Bug。

---

## 18. 数据模型

### 18.1 forgeops_feedback

```text
id
project_id
type
title
description
expected_behavior
reporter_id
environment
page_url
status

frontend_version
frontend_commit
backend_version
backend_commit

multica_issue_id
pr_url
ci_pipeline_id
deployment_version

created_at
updated_at
```

### 18.2 forgeops_context

```text
id
feedback_id
schema_version
context_json JSONB
created_at
```

Context 采用 append / snapshot 思路，避免覆盖历史上下文。

### 18.3 forgeops_feedback_comment

```text
id
feedback_id
author_id
author_type
content
attachments
created_at
```

### 18.4 forgeops_feedback_verification

```text
id
feedback_id
verifier_id
result
comment
created_at
```

### 18.5 forgeops_integration_event

记录外部系统回调：

```text
id
feedback_id
source
external_event_id
event_type
payload
status
created_at
```

用于幂等、审计、问题排查。

---

## 19. 安全设计

### 19.1 数据最小化

Feedback SDK 默认采用白名单采集，而不是黑名单删除。

禁止采集：

```text
密码
Token
Cookie
Authorization
患者姓名
身份证
手机号
病历号
完整请求 Body
完整 Response Body
```

必要的业务标识必须经过：

```text
Mask
Hash
Alias
```

### 19.2 Agent 权限最小化

Runtime 凭据：

```text
仅当前项目 Repo
仅允许创建分支 / PR
禁止 main force push
不提供生产数据库凭据
不提供生产发布凭据
```

### 19.3 Audit

至少记录：

```text
谁提交反馈
Gateway 自动补充了什么数据
哪个 Agent 执行
执行使用哪个 Runtime
改了哪些文件
创建哪个 PR
谁 Review
哪个 CI 构建
谁最终验证
```

---

## 20. V0.1 部署拓扑

建议内部测试环境：

```text
Company Network

┌─────────────────────┐
│ Existing Test App   │
│ Vue + Spring Boot   │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ ForgeOps Gateway │
│ Spring Boot         │
│ PostgreSQL          │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Multica Server      │
│ PostgreSQL          │
└──────────┬──────────┘
           │
      WebSocket / Task
           │
           ▼
┌─────────────────────┐
│ Agent Runtime Host  │
│ Container / VM      │
│ Claude Code / Codex │
│ Scoped Git Token    │
└──────────┬──────────┘
           │
           ▼
       Existing Git
           │
           ▼
       Existing CI/CD
```

首版全部部署在公司内部网络，不接生产项目。

---

## 21. 推荐仓库结构

建议正式仓库命名为：

```text
forgeops
```

采用 Monorepo 管理 ForgeOps 自研能力与第三方平台配置。

推荐 Monorepo：

```text
forgeops/

├── gateway/
│   ├── forgeops-gateway/
│   └── README.md
│
├── sdk/
│   └── forgeops-feedback-vue/
│
├── registry/
│   └── projects/
│       └── health-platform.yaml
│
├── schemas/
│   ├── feedback.schema.json
│   └── context-pack.schema.json
│
├── multica/
│   ├── skills/
│   │   ├── triage/
│   │   └── coding/
│   ├── runbooks/
│   └── templates/
│
├── policy/
│   ├── agent-policy.yaml
│   ├── pii-policy.yaml
│   └── severity-policy.yaml
│
├── deploy/
│   ├── docker-compose/
│   └── env/
│
└── docs/
    ├── architecture.md
    ├── integration-guide.md
    └── runbook.md
```

后期再增加：

```text
keep/
holmes/
edge/forgeops-edge/
```

---

## 22. V0.1 代码模块建议

ForgeOps Gateway：

```text
com.company.forgeops

├── feedback
│   ├── api
│   ├── application
│   └── domain
│
├── context
│   ├── builder
│   ├── log
│   ├── request
│   └── git
│
├── project
│   ├── registry
│   └── config
│
├── policy
│   ├── agent
│   └── security
│
├── multica
│   ├── client
│   └── mapper
│
├── cicd
│   ├── callback
│   └── adapter
│
├── verification
│
└── audit
```

保持模块化单体，不拆微服务。

V0.1 规模完全没有必要微服务化。

---

## 23. 实施阶段

### Phase 0：基础打底

完成：

```text
Multica self-host
独立 Runtime Host
Claude Code / Codex
Git 权限
试点项目 Project Registry
Request ID 规范
```

验收：

```text
人工创建一个 Multica Issue
→ Agent 能 checkout 试点 Repo
→ 修改代码
→ 创建 Draft PR
```

### Phase 1：反馈入口

完成：

```text
Vue Feedback SDK
ForgeOps Gateway
Feedback DB
Screenshot
Request Buffer
Engineering Context Pack V1
```

验收：

```text
测试人员在业务页面提交问题
→ Gateway 自动生成 Feedback
→ 自动创建 Multica Issue
```

### Phase 2：Agent 闭环

完成：

```text
Triage Skill
Coding Skill
Context 注入
Agent 修改代码
Agent Test
Draft PR
```

验收：

```text
真实 Bug
→ 自动分析
→ 自动修改
→ Draft PR
→ 开发人员只需 Review
```

### Phase 3：CI / 验证闭环

完成：

```text
Git callback
CI callback
Deployment callback
待验证通知
验证通过
Reopen
```

验收：

```text
PR Merge
→ 构建
→ 发布测试环境
→ 原反馈人收到待验证
→ 验证通过
→ Feedback Closed
```

### Phase 4：自动错误发现（V0.2）

再引入：

```text
Keep
Sentry / GlitchTip
OpenTelemetry
```

流程：

```text
Frontend / Backend Error
        ↓
Keep
        ↓
Dedup / Correlation
        ↓
ForgeOps Policy
        ↓
Multica
```

### Phase 5：SRE RCA（V0.3）

引入 HolmesGPT：

```text
Unknown Incident
      ↓
HolmesGPT
      ↓
Logs / Metrics / Trace / DB
      ↓
RCA
      ↓
Need Code Change?
      ↓
Multica
```

---

## 24. V0.1 验收指标

首版不要以“AI 自动修复率”作为唯一指标。

建议观察：

### 流程指标

```text
反馈提交 → Multica Issue 创建耗时
反馈提交 → Triage 完成耗时
反馈提交 → Draft PR 耗时
PR Review 修改次数
发布 → 原反馈人验证耗时
```

### AI 效果指标

```text
Agent 成功定位率
Agent 成功生成可 Review PR 比例
PR 一次 Review 通过率
Agent 修改后测试通过率
Reopen 比例
```

### 效率指标

最重要的是：

```text
开发人员平均手工调查时间
开发人员平均整理 AI Prompt / Context 时间
一次反馈平均人工触点数量
```

如果 V0.1 能显著减少：

```text
人工重现
人工找日志
人工复制 StackTrace
人工找对应 Repo
人工整理 Prompt
人工通知测试
```

项目就已经成功。

---

## 25. 风险与控制

### 25.1 Agent 修改错误

控制：

```text
Draft PR
Human Review
Test Gate
禁止 Auto Merge
```

### 25.2 Context 不足

控制：

```text
NEED_INFO 状态
原反馈页面追加信息
Agent Resume
```

### 25.3 敏感信息进入 LLM

控制：

```text
SDK 白名单采集
Gateway PII Sanitizer
Context Pack 二次过滤
Runtime 最小权限
```

### 25.4 Agent Runtime 权限过大

控制：

```text
独立 Runtime Host
Container / VM
Dedicated User
Scoped Git Token
禁止生产凭据
```

### 25.5 平台过早复杂化

控制：

V0.1 不引入：

```text
Kafka
微服务
复杂 Workflow Designer
HolmesGPT
Keep 主链路
OpenHands
ForgeOps Edge
生产自动修复
```

---

## 26. 架构演进路线

```text
V0.1
Human Feedback
     ↓
ForgeOps Gateway
     ↓
Multica
     ↓
Agent
     ↓
PR / CI / Verify


V0.2
Runtime Error
     ↓
Keep
     ↓
ForgeOps Policy
     ↓
Multica


V0.3
Complex Incident
     ↓
Keep
     ↓
HolmesGPT
     ↓
RCA
     ↓
Multica


V0.4
Hospital / Gov Isolated Network
     ↓
ForgeOps Edge
     ↓
Sanitize / Buffer / Encrypt
     ↓
Central Platform
```

最终形成：

```text
Event Control Plane
          Keep
           │
           ▼
ForgeOps Policy Layer
           │
     ┌─────┴─────┐
     │           │
HolmesGPT      Multica
  SRE RCA      Agent Work
                 │
                 ▼
        Claude Code / Codex
                 │
                 ▼
               Git/CI
```

---

## 27. 最终架构决议

V0.1 正式决议如下：

1. **以公司内部 Vue + Spring Boot 测试环境作为首个试点项目。**
2. **第一版优先改造人工反馈流程，不优先建设自动线上故障修复。**
3. **在业务系统内部嵌入 Feedback SDK，避免测试 / 产品额外学习新平台。**
4. **建设轻量 ForgeOps Gateway，作为公司业务侧接入层，而不是重新造 Agent 平台。**
5. **Multica 作为唯一 Agent / Engineering Work Control Plane。**
6. **Claude Code / Codex 通过独立 Runtime Pool 执行，不与 Multica Server 共用高权限运行账户。**
7. **Agent 自动化终点为 Draft PR；Merge、正式 Review 保留人工 Gate。**
8. **继续复用现有 Git 与 CI/CD，不重建发布系统。**
9. **部署完成后自动回告原反馈人，验证结果进入同一 Feedback / Issue Timeline。**
10. **Keep 延后到 V0.2，用于自动异常采集、Fingerprint、Dedup、Correlation。**
11. **HolmesGPT 延后到 V0.3，用于复杂 SRE Incident 的根因调查。**
12. **OpenHands 不作为首版部署组件，只吸收其 Runtime / Workspace / Sandbox 设计思想。**
13. **公司长期真正掌握的核心资产是 Project Registry、ForgeOps Policy、Engineering Context Pack、PII Policy，以及未来的 ForgeOps Edge。**

---

## 28. 下一步实施入口

建议下一步直接进入 **Phase 0 + Phase 1**，优先完成以下四件事情：

```text
1. Multica + 独立 Runtime Host 跑通一次人工 Issue → Draft PR

2. 为试点 Vue 项目建立 Feedback SDK 最小版本

3. 为 Spring Boot 链路统一 Request ID / Trace ID / Version / Commit 信息

4. 建立 ForgeOps Gateway + Project Registry + Engineering Context Pack V1
```

只要这四项打通，就可以开始让真实测试 / 产品人员参与试运行，并用真实反馈推动后续迭代。

