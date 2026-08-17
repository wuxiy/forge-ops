import { RequestContextCollector, attachAxios } from './collector'
import { ForgeOpsGatewayClient } from './gateway'
import { ulid } from './ulid'
import type { FeedbackSubmission, ForgeOpsOptions, ReporterInfo } from './types'

export * from './types'
export { RequestContextCollector, attachAxios, ForgeOpsGatewayClient, ulid }

/** 采集上下文（供各框架壳 / DOM Widget 共用）：组装白名单提交载荷。 */
export class FeedbackCore {
  readonly options: ForgeOpsOptions
  readonly collector: RequestContextCollector
  readonly client: ForgeOpsGatewayClient
  private reporterName = ''

  constructor(options: ForgeOpsOptions) {
    this.options = options
    this.collector = new RequestContextCollector(options.requestBufferSize ?? 50)
    this.client = new ForgeOpsGatewayClient(options.gatewayUrl)
  }

  get enabled(): boolean {
    const allow = this.options.enabledEnvironments ?? ['test', 'uat', 'staging']
    return allow.includes(this.options.environment)
  }

  /** 上次提交人（「我的反馈」默认查询者）。 */
  get lastReporterName(): string {
    return this.reporterName
  }

  setReporterName(name: string): void {
    this.reporterName = name
  }

  getReporter(): ReporterInfo | null {
    const fromApp = this.options.getReporter?.()
    if (fromApp?.name) return fromApp
    return this.reporterName ? { name: this.reporterName } : null
  }

  start(): void {
    if (this.enabled) this.collector.start()
  }

  buildSubmission(form: {
    type: FeedbackSubmission['type']
    title: string
    description: string
    expectedBehavior: string
    steps: string
    note: string
    reporterName: string
    screenshot?: string
  }): FeedbackSubmission {
    const opts = this.options
    const frontend = opts.getFrontendInfo?.() || {}
    const backend = opts.getBackendInfo?.() || {}
    return {
      schemaVersion: '1.0',
      projectId: opts.projectId,
      type: form.type,
      title: form.title || undefined,
      description: form.description,
      expectedBehavior: form.expectedBehavior || undefined,
      steps: form.steps || undefined,
      note: form.note || undefined,
      reporter: this.getReporter() ?? { name: form.reporterName },
      environment: opts.environment,
      page: {
        url: location.href,
        route: opts.getRouteName?.(),
        title: document.title,
      },
      client: {
        userAgent: navigator.userAgent,
        platform: navigator.platform,
        language: navigator.language,
        screen: `${screen.width}x${screen.height}`,
        viewport: `${innerWidth}x${innerHeight}`,
      },
      frontend,
      backend: backend.version || backend.commit ? backend : undefined,
      requests: this.collector.failedRequests(),
      consoleErrors: this.collector.consoleErrorList(),
      screenshot: form.screenshot,
      occurredAt: new Date().toISOString(),
    }
  }
}
