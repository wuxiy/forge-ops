import type { BrowserContext, FeedbackDraft, FeedbackView, ForgeOpsOptions } from './types'

/** Minimal client for the v2 feedback boundary; it has no anonymous-name or v1 fallback mode. */
export class ForgeOpsGatewayClient {
  constructor(private readonly options: Pick<ForgeOpsOptions, 'gatewayUrl' | 'projectId' | 'getToken'>) {}

  async submit(draft: FeedbackDraft, browserContext: BrowserContext): Promise<FeedbackView> {
    return this.request('', { method: 'POST', body: JSON.stringify({ ...draft, browserContext }) })
  }

  async mine(): Promise<FeedbackView[]> {
    return this.request('/mine')
  }

  async get(feedbackId: string): Promise<FeedbackView> {
    return this.request(`/${encodeURIComponent(feedbackId)}`)
  }

  async reopen(feedbackId: string, reason: string): Promise<FeedbackView> {
    return this.request(`/${encodeURIComponent(feedbackId)}/reopen`, { method: 'POST', body: JSON.stringify({ reason }) })
  }

  private async request<T>(suffix: string, init: RequestInit = {}): Promise<T> {
    const token = await this.options.getToken()
    if (!token) throw new Error('ForgeOps requires a host-issued identity token')
    const response = await fetch(`${this.options.gatewayUrl.replace(/\/$/, '')}/api/v2/projects/${encodeURIComponent(this.options.projectId)}/feedback${suffix}`, {
      ...init,
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...(init.headers ?? {}) },
    })
    if (!response.ok) throw new Error(`ForgeOps request failed (${response.status})`)
    return response.json() as Promise<T>
  }
}
