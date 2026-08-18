export declare function initForgeOpsFeedback(options: ForgeOpsOptions, target?: HTMLElement): ForgeOpsFeedbackHandle

export declare function mountWidget(container: HTMLElement, core: {
  options: ForgeOpsOptions
  collector: RequestContextCollector
  client: ForgeOpsGatewayClient
  buildSubmission(form: WidgetForm): FeedbackSubmission
  lastReporterName: string
  setReporterName(name: string): void
  getReporter(): ReporterInfo | null
  enabled: boolean
}): MountedWidget

export declare function attachAxios(instance: unknown, collector: unknown): void

export interface ForgeOpsFeedbackHandle {
  core: unknown
  widget: MountedWidget | null
  open(): void
  close(): void
  destroy(): void
}
export interface MountedWidget {
  open(): void
  close(): void
  destroy(): void
}
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
export interface WidgetForm {
  type: FeedbackType
  title: string
  description: string
  expectedBehavior: string
  steps: string
  note: string
  reporterName: string
  screenshot?: string
}
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
  client?: { userAgent?: string; platform?: string; language?: string; screen?: string; viewport?: string }
  frontend?: { version?: string; commit?: string }
  backend?: { version?: string; commit?: string }
  requests: RequestSummary[]
  consoleErrors: string[]
  screenshot?: string
  occurredAt: string
}
export interface ForgeOpsOptions {
  gatewayUrl: string
  projectId: string
  environment: FeedbackEnvironment
  enabledEnvironments?: FeedbackEnvironment[]
  getReporter?: () => ReporterInfo | null | undefined
  getFrontendInfo?: () => { version?: string; commit?: string }
  getBackendInfo?: () => { version?: string; commit?: string } | null | undefined
  requestBufferSize?: number
  getRouteName?: () => string | undefined
}
export interface RequestContextCollector {
  failedRequests(max?: number): RequestSummary[]
  recentRequests(): RequestSummary[]
  consoleErrorList(): string[]
  start(): void
  stop(): void
}
export interface ForgeOpsGatewayClient {
  submitFeedback(p: FeedbackSubmission): Promise<{ id: string; status: string }>
  listMyFeedback(reporter: string, projectId: string): Promise<FeedbackListItem[]>
  getFeedback(id: string): Promise<FeedbackDetail>
  verifyPass(id: string, verifierName: string, comment?: string): Promise<unknown>
  verifyFail(id: string, verifierName: string, comment: string, extra?: { requests?: RequestSummary[]; consoleErrors?: string[] }): Promise<unknown>
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
export interface FeedbackDetail extends FeedbackListItem {
  description: string
  expectedBehavior?: string | null
  pageUrl?: string | null
  frontendVersion?: string | null
  backendVersion?: string | null
  multicaIssueUrl?: string | null
  comments: { id: number; authorType: string; author: string; content: string; createdAt: string }[]
}
