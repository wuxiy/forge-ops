import type { FeedbackDetail, FeedbackListItem, FeedbackSubmission, RequestSummary } from './types'

/** ForgeOps Gateway API 客户端（§9.3）。 */
export class ForgeOpsGatewayClient {
  constructor(private baseUrl: string) {}

  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    const res = await fetch(`${this.baseUrl.replace(/\/$/, '')}${path}`, {
      headers: { 'Content-Type': 'application/json', ...(init?.headers || {}) },
      ...init,
    })
    if (!res.ok) {
      let message = `HTTP ${res.status}`
      try {
        const body = await res.json()
        if (body?.message) message = body.message
      } catch {
        /* ignore */
      }
      throw new Error(message)
    }
    return res.json() as Promise<T>
  }

  submitFeedback(payload: FeedbackSubmission): Promise<{ id: string; status: string }> {
    return this.request('/api/v1/feedback', { method: 'POST', body: JSON.stringify(payload) })
  }

  listMyFeedback(reporter: string, projectId: string): Promise<FeedbackListItem[]> {
    const query = `reporter=${encodeURIComponent(reporter)}&projectId=${encodeURIComponent(projectId)}`
    return this.request(`/api/v1/feedback?${query}`)
  }

  getFeedback(id: string): Promise<FeedbackDetail> {
    return this.request(`/api/v1/feedback/${encodeURIComponent(id)}`)
  }

  comment(id: string, content: string): Promise<unknown> {
    return this.request(`/api/v1/feedback/${encodeURIComponent(id)}/comment`, {
      method: 'POST',
      body: JSON.stringify({ content }),
    })
  }

  verifyPass(id: string, comment?: string): Promise<unknown> {
    return this.request(`/api/v1/feedback/${encodeURIComponent(id)}/verify`, {
      method: 'POST',
      body: JSON.stringify({ result: 'PASS', comment }),
    })
  }

  verifyFail(id: string, comment: string, extra?: { requests?: RequestSummary[]; consoleErrors?: string[] }): Promise<unknown> {
    return this.request(`/api/v1/feedback/${encodeURIComponent(id)}/reopen`, {
      method: 'POST',
      body: JSON.stringify({ comment, requests: extra?.requests || [], consoleErrors: extra?.consoleErrors || [] }),
    })
  }
}
