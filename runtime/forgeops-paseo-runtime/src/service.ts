import { resolve } from 'node:path'
import type { RuntimeConfig } from './config.js'
import { RunStore } from './store.js'
import type { ExecutionRequest, ExecutionSnapshot, PaseoAdapter, PersistedRun, RunState } from './types.js'

/** Deep module boundary: all callers see only submit, inspect and cancel. */
export class ExecutionService {
  private readonly submitting = new Map<string, Promise<ExecutionSnapshot>>()

  constructor(private readonly config: RuntimeConfig, private readonly store: RunStore, private readonly paseo: PaseoAdapter) {}

  async submit(input: ExecutionRequest): Promise<ExecutionSnapshot> {
    validate(input, this.config)
    const inFlight = this.submitting.get(input.idempotencyKey)
    if (inFlight) return inFlight
    const existing = this.store.get(input.idempotencyKey)
    if (existing) return toSnapshot(existing)
    const submission = this.submitNew(input)
    this.submitting.set(input.idempotencyKey, submission)
    try {
      return await submission
    } finally {
      this.submitting.delete(input.idempotencyKey)
    }
  }

  private async submitNew(input: ExecutionRequest): Promise<ExecutionSnapshot> {
    const pending: PersistedRun = {
      idempotencyKey: input.idempotencyKey,
      projectId: input.projectId,
      role: input.role,
      cwd: resolve(input.cwd),
      providerRunId: null,
      state: 'SUBMITTING',
      updatedAt: new Date().toISOString(),
    }
    await this.store.save(pending)
    try {
      const created = await this.paseo.create(input)
      const run: PersistedRun = { ...pending, providerRunId: created.id, state: mapState(created.status), updatedAt: new Date().toISOString() }
      if (created.lastError?.message) run.failureCategory = 'PASEO_ERROR'
      await this.store.save(run)
      return toSnapshot(run)
    } catch (error) {
      const failed: PersistedRun = { ...pending, state: 'FAILED', failureCategory: 'PASEO_SUBMIT_FAILED', updatedAt: new Date().toISOString() }
      await this.store.save(failed)
      return toSnapshot(failed)
    }
  }

  async inspect(idempotencyKey: string): Promise<ExecutionSnapshot | null> {
    const record = this.store.get(idempotencyKey)
    if (!record) return null
    if (!record.providerRunId || terminal(record.state)) return toSnapshot(record)
    try {
      const provider = await this.paseo.inspect(record.providerRunId)
      if (!provider) return toSnapshot(record)
      const updated: PersistedRun = { ...record, state: mapState(provider.status), updatedAt: new Date().toISOString() }
      if (provider.lastError?.message) updated.failureCategory = 'PASEO_ERROR'
      await this.store.save(updated)
      return toSnapshot(updated)
    } catch {
      return toSnapshot(record)
    }
  }

  async cancel(idempotencyKey: string): Promise<ExecutionSnapshot | null> {
    const record = this.store.get(idempotencyKey)
    if (!record) return null
    if (!record.providerRunId || terminal(record.state)) return toSnapshot(record)
    try {
      await this.paseo.cancel(record.providerRunId)
      const cancelled: PersistedRun = { ...record, state: 'CANCELLED', updatedAt: new Date().toISOString() }
      await this.store.save(cancelled)
      return toSnapshot(cancelled)
    } catch {
      const failed: PersistedRun = { ...record, state: 'FAILED', failureCategory: 'PASEO_CANCEL_FAILED', updatedAt: new Date().toISOString() }
      await this.store.save(failed)
      return toSnapshot(failed)
    }
  }
}

function validate(input: ExecutionRequest, config: RuntimeConfig): void {
  if (!input.idempotencyKey?.trim() || !input.projectId?.trim() || !input.prompt?.trim() || !input.outputSchema || !input.cwd?.trim()) {
    throw new Error('idempotencyKey, projectId, cwd, prompt and outputSchema are required')
  }
  if (input.role !== 'TRIAGE' && input.role !== 'CODING') throw new Error('role must be TRIAGE or CODING')
  const cwd = resolve(input.cwd)
  if (!config.allowedRoots.some((root) => cwd.startsWith(`${root}/`) || cwd === root)) throw new Error('cwd is outside the configured project allowlist')
}

function mapState(status: string | null): RunState {
  if (status === 'completed' || status === 'idle') return 'SUCCEEDED'
  if (status === 'cancelled') return 'CANCELLED'
  if (status === 'timed_out') return 'TIMED_OUT'
  if (status === 'failed' || status === 'error') return 'FAILED'
  return 'RUNNING'
}

function terminal(state: RunState): boolean {
  return state === 'SUCCEEDED' || state === 'FAILED' || state === 'CANCELLED' || state === 'TIMED_OUT'
}

function toSnapshot(record: PersistedRun): ExecutionSnapshot {
  return { idempotencyKey: record.idempotencyKey, providerRunId: record.providerRunId, state: record.state, failureCategory: record.failureCategory, updatedAt: record.updatedAt }
}
