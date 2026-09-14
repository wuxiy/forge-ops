import { RequestContextCollector } from './collector'
import { ForgeOpsGatewayClient } from './gateway'
import type { BrowserContext, FeedbackDraft, FeedbackView, ForgeOpsOptions } from './types'

/** One framework-neutral core owns collector lifecycle and v2 API calls. */
export class FeedbackCore {
  readonly collector: RequestContextCollector
  readonly client: ForgeOpsGatewayClient
  private started = false

  constructor(readonly options: ForgeOpsOptions) {
    this.collector = new RequestContextCollector()
    this.client = new ForgeOpsGatewayClient(options)
  }

  get enabled(): boolean {
    return this.options.enabled !== false
  }

  start(): void {
    if (this.started || !this.enabled) return
    this.started = true
    this.collector.start()
  }

  stop(): void {
    if (!this.started) return
    this.started = false
    this.collector.stop()
  }

  submit(draft: FeedbackDraft): Promise<FeedbackView> {
    return this.client.submit(draft, this.browserContext())
  }

  mine(): Promise<FeedbackView[]> {
    return this.client.mine()
  }

  reopen(id: string, reason: string): Promise<FeedbackView> {
    return this.client.reopen(id, reason)
  }

  private browserContext(): BrowserContext {
    if (typeof window === 'undefined') return {}
    const failed = this.collector.failedRequests()
    const errors = this.collector.consoleErrors()
    return {
      url: window.location.href,
      route: this.options.getRoute?.(),
      console: errors.length ? errors.join('\n') : undefined,
      requestSummary: failed.length ? JSON.stringify(failed) : undefined,
    }
  }
}
