import type { RequestSummary } from './types'
import { ulid } from './ulid'

/**
 * 前端请求缓冲区 + Console 错误采集（架构文档 §7.3）。
 * 白名单字段：method/url/status/duration/requestId/traceId/time —— 不采集
 * Header、请求体、响应体，Authorization/Cookie/Token 一律不入缓冲。
 */
export class RequestContextCollector {
  private buffer: RequestSummary[] = []
  private consoleErrors: string[] = []
  private bufferSize = 50
  private originalFetch: typeof fetch | null = null
  private consoleErrorPatched = false

  constructor(bufferSize = 50) {
    this.bufferSize = bufferSize
  }

  start(): void {
    this.patchFetch()
    this.patchConsoleError()
  }

  stop(): void {
    if (this.originalFetch) {
      window.fetch = this.originalFetch
      this.originalFetch = null
    }
  }

  private patchFetch(): void {
    if (this.originalFetch || typeof window === 'undefined') return
    const original = window.fetch.bind(window)
    this.originalFetch = window.fetch

    window.fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
      const method = (init?.method || (input instanceof Request ? input.method : 'GET')).toUpperCase()
      const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url
      const requestId = input instanceof Request ? (input.headers.get('X-Request-ID') ?? ulid()) : ulid()
      const start = performance.now()

      const headers = new Headers(init?.headers || (input instanceof Request ? input.headers : undefined))
      if (!headers.has('X-Request-ID')) headers.set('X-Request-ID', requestId)

      try {
        const response = await original(input, { ...init, headers })
        this.record({
          method,
          url,
          status: response.status,
          durationMs: Math.round(performance.now() - start),
          requestId: response.headers.get('X-Request-ID') ?? requestId,
          time: new Date().toISOString(),
        })
        return response
      } catch (err) {
        this.record({
          method,
          url,
          status: 0,
          durationMs: Math.round(performance.now() - start),
          requestId,
          time: new Date().toISOString(),
        })
        throw err
      }
    }
  }

  private patchConsoleError(): void {
    if (this.consoleErrorPatched || typeof console === 'undefined') return
    this.consoleErrorPatched = true
    const original = console.error.bind(console)
    console.error = (...args: unknown[]) => {
      try {
        const text = args
          .map((a) => {
            if (a instanceof Error) return `${a.name}: ${a.message}`
            if (typeof a === 'string') return a
            try {
              return JSON.stringify(a)
            } catch {
              return String(a)
            }
          })
          .join(' ')
          .slice(0, 2000)
        if (text) {
          this.consoleErrors.push(`[${new Date().toISOString()}] ${text}`)
          if (this.consoleErrors.length > 10) this.consoleErrors.shift()
        }
      } catch {
        /* 采集失败不阻断业务 */
      }
      original(...args)
    }
  }

  private record(entry: RequestSummary): void {
    this.buffer.push(entry)
    if (this.buffer.length > this.bufferSize) this.buffer.shift()
  }

  /** 最近失败的请求（4xx/5xx/网络错误），供反馈自动附带。 */
  failedRequests(max = 10): RequestSummary[] {
    return this.buffer.filter((r) => r.status === undefined || r.status >= 400 || r.status === 0).slice(-max)
  }

  recentRequests(): RequestSummary[] {
    return [...this.buffer]
  }

  consoleErrorList(): string[] {
    return [...this.consoleErrors]
  }
}
