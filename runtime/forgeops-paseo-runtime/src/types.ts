export type RunRole = 'TRIAGE' | 'CODING'
export type RunState = 'QUEUED' | 'SUBMITTING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'TIMED_OUT'

export interface ExecutionRequest {
  idempotencyKey: string
  projectId: string
  role: RunRole
  cwd: string
  prompt: string
  outputSchema: Record<string, unknown>
  timeoutMs: number
}

export interface ExecutionSnapshot {
  idempotencyKey: string
  providerRunId: string | null
  state: RunState
  failureCategory?: string
  resultJson?: string
  resultError?: string
  updatedAt: string
}

export interface PersistedRun extends ExecutionSnapshot {
  projectId: string
  role: RunRole
  cwd: string
  deadlineAt: string
}

export interface PaseoAgentSnapshot {
  id: string
  status: string | null
  lastError?: { message?: string } | null
  resultJson?: string
  resultError?: string
}

export interface PaseoAdapter {
  create(input: ExecutionRequest): Promise<PaseoAgentSnapshot>
  inspect(providerRunId: string): Promise<PaseoAgentSnapshot | null>
  cancel(providerRunId: string): Promise<void>
  close(): Promise<void>
}
