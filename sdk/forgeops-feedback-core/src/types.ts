/** Framework-neutral ForgeOps 2.0 browser contract. */
export interface ForgeOpsOptions {
  gatewayUrl: string
  projectId: string
  /** Obtains a short-lived token from the host application's authenticated backend. */
  getToken: () => string | Promise<string>
  getRoute?: () => string | undefined
  enabled?: boolean
}

export interface FeedbackDraft {
  title: string
  description: string
}

export interface BrowserContext {
  url?: string
  route?: string
  userAction?: string
  console?: string
  requestSummary?: string
}

export interface FeedbackView {
  id: string
  displayNo: number
  state: string
}
