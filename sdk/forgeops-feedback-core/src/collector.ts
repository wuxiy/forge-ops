export interface RequestSummary {
  method: string
  url: string
  status: number
  durationMs: number
  requestId: string
  time: string
}

type BrowserRuntime = {
  collectors: Set<RequestContextCollector>
  originalFetch?: typeof fetch
  originalConsoleError?: typeof console.error
}

const RUNTIME_KEY = Symbol.for('forgeops.v2.collector.runtime')

function runtime(): BrowserRuntime {
  const root = globalThis as typeof globalThis & { [RUNTIME_KEY]?: BrowserRuntime }
  if (!root[RUNTIME_KEY]) root[RUNTIME_KEY] = { collectors: new Set() }
  return root[RUNTIME_KEY]!
}

/**
 * One global fetch/console wrapper fans out only metadata to active collectors. No headers,
 * request bodies, response bodies, cookies, tokens or screenshots are captured.
 */
export class RequestContextCollector {
  private readonly requests: RequestSummary[] = []
  private readonly errors: string[] = []
  private started = false

  constructor(private readonly bufferSize = 50) {}

  start(): void {
    if (this.started || typeof window === 'undefined') return
    this.started = true
    const state = runtime()
    state.collectors.add(this)
    if (state.collectors.size === 1) install(state)
  }

  stop(): void {
    if (!this.started || typeof window === 'undefined') return
    this.started = false
    const state = runtime()
    state.collectors.delete(this)
    if (state.collectors.size === 0) restore(state)
  }

  failedRequests(max = 10): RequestSummary[] {
    return this.requests.filter((entry) => entry.status === 0 || entry.status >= 400).slice(-max)
  }

  consoleErrors(max = 10): string[] {
    return this.errors.slice(-max)
  }

  recordRequest(entry: RequestSummary): void {
    this.requests.push(entry)
    if (this.requests.length > this.bufferSize) this.requests.shift()
  }

  recordError(entry: string): void {
    this.errors.push(entry.slice(0, 2000))
    if (this.errors.length > 10) this.errors.shift()
  }
}

function install(state: BrowserRuntime): void {
  state.originalFetch = window.fetch
  state.originalConsoleError = console.error
  window.fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const source = state.originalFetch!
    const method = (init?.method ?? (input instanceof Request ? input.method : 'GET')).toUpperCase()
    const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url
    const requestId = crypto.randomUUID()
    const startedAt = performance.now()
    try {
      const response = await source(input, init)
      publishRequest(state, { method, url, status: response.status, durationMs: Math.round(performance.now() - startedAt), requestId, time: new Date().toISOString() })
      return response
    } catch (error) {
      publishRequest(state, { method, url, status: 0, durationMs: Math.round(performance.now() - startedAt), requestId, time: new Date().toISOString() })
      throw error
    }
  }
  console.error = (...args: unknown[]) => {
    const text = args.map(safeText).join(' ')
    for (const collector of state.collectors) collector.recordError(`[${new Date().toISOString()}] ${text}`)
    state.originalConsoleError!.apply(console, args)
  }
}

function restore(state: BrowserRuntime): void {
  if (state.originalFetch) window.fetch = state.originalFetch
  if (state.originalConsoleError) console.error = state.originalConsoleError
  state.originalFetch = undefined
  state.originalConsoleError = undefined
}

function publishRequest(state: BrowserRuntime, entry: RequestSummary): void {
  for (const collector of state.collectors) collector.recordRequest(entry)
}

function safeText(value: unknown): string {
  if (value instanceof Error) return `${value.name}: ${value.message}`
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}
