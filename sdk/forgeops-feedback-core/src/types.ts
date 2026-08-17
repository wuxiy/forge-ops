export type FeedbackType = 'BUG' | 'OPTIMIZATION' | 'REQUIREMENT'

export type FeedbackEnvironment = 'test' | 'uat' | 'staging'

export interface RequestSummary {
  method: string
  url: string
  status?: number
  durationMs?: number
  requestId?: string
  traceId?: string
  time: string
}

export interface ReporterInfo {
  id?: string
  name: string
}

/** SDK -> Gateway 提交载荷，与 schemas/feedback.schema.json 一一对应（白名单）。 */
export interface FeedbackSubmission {
  schemaVersion: '1.0'
  projectId: string
  type: FeedbackType
  title?: string
  description: string
  expectedBehavior?: string
  steps?: string
  note?: string
  reporter: ReporterInfo
  environment: FeedbackEnvironment
  page?: { url?: string; route?: string; title?: string }
  client?: {
    userAgent?: string
    platform?: string
    language?: string
    screen?: string
    viewport?: string
  }
  frontend?: { version?: string; commit?: string }
  backend?: { version?: string; commit?: string }
  requests: RequestSummary[]
  consoleErrors: string[]
  screenshot?: string
  occurredAt: string
}

export interface ForgeOpsOptions {
  /** ForgeOps Gateway 基础地址，如 http://gateway.example.internal */
  gatewayUrl: string
  projectId: string
  environment: FeedbackEnvironment
  /** 仅在这些环境启用，默认 ['test','uat','staging'] */
  enabledEnvironments?: FeedbackEnvironment[]
  /** 提供当前登录用户（宿主应用注入），否则要求表单填写 */
  getReporter?: () => ReporterInfo | null | undefined
  /** 前端版本信息（构建注入） */
  getFrontendInfo?: () => { version?: string; commit?: string }
  /** 后端版本信息（启动时从 /api/version 拉取后注入） */
  getBackendInfo?: () => { version?: string; commit?: string } | null | undefined
  /** 请求缓冲区大小，默认 50 */
  requestBufferSize?: number
  /** 路由名称提取（配合 vue-router / react-router） */
  getRouteName?: () => string | undefined
}

export interface FeedbackListItem {
  id: string
  projectId: string
  type: FeedbackType
  title: string
  status: string
  displayStatus: string
  reporter: string
  environment: string
  createdAt: string
  updatedAt: string
  deploymentVersion?: string | null
  prUrl?: string | null
}

export interface FeedbackTimelineItem {
  id: number
  authorType: string
  author: string
  content: string
  createdAt: string
}

export interface FeedbackDetail extends FeedbackListItem {
  description: string
  expectedBehavior?: string | null
  pageUrl?: string | null
  frontendVersion?: string | null
  backendVersion?: string | null
  multicaIssueUrl?: string | null
  comments: FeedbackTimelineItem[]
}
