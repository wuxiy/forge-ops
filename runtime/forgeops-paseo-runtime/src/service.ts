import { resolve } from 'node:path'
import type { RuntimeConfig } from './config.js'
import { RunStore } from './store.js'
import { isVerificationFamily, type ExecutionRequest, type ExecutionSnapshot, type PaseoAdapter, type PersistedRun, type RunState } from './types.js'

/** Deep module boundary: all callers see only submit, inspect and cancel. */
export class ExecutionService {
  private readonly submitting = new Map<string, Promise<ExecutionSnapshot>>()
  private readonly projectGates = new Map<string, Promise<void>>()

  constructor(private readonly config: RuntimeConfig, private readonly store: RunStore, private readonly paseo: PaseoAdapter) {}

  async submit(input: ExecutionRequest): Promise<ExecutionSnapshot> {
    validate(input, this.config)
    const inFlight = this.submitting.get(input.idempotencyKey)
    if (inFlight) return inFlight
    const existing = this.store.get(input.idempotencyKey)
    // A record without a provider id is not a durable answer: a lost create response (the daemon
    // may already run the task) or a queued-for-outage attempt must be retried through the
    // recover-by-title adapter path, never by returning a stale placeholder.
    if (existing && (existing.providerRunId || terminal(existing.state))) return toSnapshot(existing)
    const submission = this.inProjectGate(input.projectId, async () => {
      const durable = this.store.get(input.idempotencyKey)
      if (durable && (durable.providerRunId || terminal(durable.state))) return toSnapshot(durable)
      // A retry of the same unresolved record does not consume another concurrency slot.
      if (!durable && this.activeForProject(input.projectId) >= this.config.maxConcurrentPerProject) return queued(input.idempotencyKey)
      return this.submitNew(input)
    })
    this.submitting.set(input.idempotencyKey, submission)
    try {
      return await submission
    } finally {
      this.submitting.delete(input.idempotencyKey)
    }
  }

  private activeForProject(projectId: string): number {
    return this.store.all().filter((record) => record.projectId === projectId
      && (record.state === 'SUBMITTING' || record.state === 'RUNNING')).length
  }

  private async inProjectGate<T>(projectId: string, action: () => Promise<T>): Promise<T> {
    const previous = this.projectGates.get(projectId) ?? Promise.resolve()
    let release: (() => void) | undefined
    const current = new Promise<void>((resolve) => { release = resolve })
    const gate = previous.then(() => current)
    this.projectGates.set(projectId, gate)
    await previous
    try {
      return await action()
    } finally {
      release?.()
      if (this.projectGates.get(projectId) === gate) this.projectGates.delete(projectId)
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
      deadlineAt: new Date(Date.now() + input.timeoutMs).toISOString(),
    }
    await this.store.save(pending)
    try {
      const created = await this.paseo.create(input)
      // A recovered or freshly created run gets a fresh deadline from this submission attempt.
      const run: PersistedRun = { ...pending, providerRunId: created.id, state: mapState(created.status), updatedAt: new Date().toISOString(), deadlineAt: new Date(Date.now() + input.timeoutMs).toISOString() }
      if (created.lastError?.message) run.failureCategory = 'PASEO_ERROR'
      await this.store.save(run)
      return toSnapshot(run)
    } catch (error) {
      // Observable failure category only; never the prompt or provider payload.
      console.error('[runtime] submit unavailable:', input.idempotencyKey, error instanceof Error ? error.message : String(error))
      // AGT-09: a daemon outage queues the run for the next reconciliation instead of failing the workflow.
      const queuedForRetry: PersistedRun = { ...pending, state: 'QUEUED', failureCategory: 'PASEO_UNAVAILABLE', updatedAt: new Date().toISOString() }
      await this.store.save(queuedForRetry)
      return toSnapshot(queuedForRetry)
    }
  }

  async inspect(idempotencyKey: string): Promise<ExecutionSnapshot | null> {
    const record = this.store.get(idempotencyKey)
    if (!record) return null
    if (!terminal(record.state) && expired(record)) return this.timeout(record)
    if (!record.providerRunId || terminal(record.state)) return toSnapshot(record)
    try {
      const provider = await this.paseo.inspect(record.providerRunId)
      if (!provider) {
        // The provider no longer knows this run (externally deleted); it can never complete.
        const vanished: PersistedRun = { ...record, state: 'FAILED', failureCategory: 'PASEO_AGENT_MISSING', updatedAt: new Date().toISOString() }
        await this.store.save(vanished)
        return toSnapshot(vanished)
      }
      const updated: PersistedRun = { ...record, state: mapState(provider.status), updatedAt: new Date().toISOString() }
      if (provider.lastError?.message) updated.failureCategory = 'PASEO_ERROR'
      await this.store.save(updated)
      return toSnapshot(updated, provider.resultJson, provider.resultError)
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

  private async timeout(record: PersistedRun): Promise<ExecutionSnapshot> {
    if (!record.providerRunId) {
      const timedOut: PersistedRun = { ...record, state: 'TIMED_OUT', failureCategory: 'SUBMISSION_TIMEOUT', updatedAt: new Date().toISOString() }
      await this.store.save(timedOut)
      return toSnapshot(timedOut)
    }
    try {
      await this.paseo.cancel(record.providerRunId)
      const provider = await this.paseo.inspect(record.providerRunId)
      if (!provider) return toSnapshot(record)
      const state = mapState(provider.status)
      if (state !== 'CANCELLED' && state !== 'TIMED_OUT') {
        // The provider finished on its own before the cancellation landed; keep its verdict.
        if (state === 'SUCCEEDED') {
          const finished: PersistedRun = { ...record, state: 'SUCCEEDED', updatedAt: new Date().toISOString() }
          await this.store.save(finished)
          return toSnapshot(finished, provider.resultJson, provider.resultError)
        }
        return toSnapshot(record)
      }
      const timedOut: PersistedRun = { ...record, state: 'TIMED_OUT', failureCategory: 'RUN_TIMEOUT', updatedAt: new Date().toISOString() }
      await this.store.save(timedOut)
      return toSnapshot(timedOut)
    } catch {
      return toSnapshot(record)
    }
  }
}

function validate(input: ExecutionRequest, config: RuntimeConfig): void {
  if (!input.idempotencyKey?.trim() || !input.projectId?.trim() || !input.prompt?.trim() || !input.outputSchema || !input.cwd?.trim()) {
    throw new Error('idempotencyKey, projectId, cwd, prompt and outputSchema are required')
  }
  const roles: ReadonlyArray<ExecutionRequest['role']> = ['TRIAGE', 'CODING', 'VERIFICATION', 'FAILURE_ANALYSIS']
  if (!roles.includes(input.role)) throw new Error('role must be TRIAGE, CODING, VERIFICATION or FAILURE_ANALYSIS')
  if (!Number.isInteger(input.timeoutMs) || input.timeoutMs < 1 || input.timeoutMs > config.defaultRunTimeoutMs) {
    throw new Error('timeoutMs must be a positive integer no greater than the Runtime limit')
  }
  const cwd = resolve(input.cwd)
  if (isVerificationFamily(input.role)) {
    // VER-07: verification-family runs may only use isolated task roots, never repository worktrees.
    if (!config.taskRoots.some((root) => cwd.startsWith(`${root}/`) && cwd !== root)) {
      throw new Error('verification-family cwd must be a dedicated directory inside the configured task roots')
    }
    return
  }
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

function queued(idempotencyKey: string): ExecutionSnapshot {
  return {
    idempotencyKey,
    providerRunId: null,
    state: 'QUEUED',
    failureCategory: 'PROJECT_CONCURRENCY_LIMIT',
    updatedAt: new Date().toISOString(),
  }
}

function expired(record: PersistedRun): boolean {
  return Number.isFinite(Date.parse(record.deadlineAt)) && Date.parse(record.deadlineAt) <= Date.now()
}

function toSnapshot(record: PersistedRun, resultJson?: string, resultError?: string): ExecutionSnapshot {
  return {
    idempotencyKey: record.idempotencyKey,
    providerRunId: record.providerRunId,
    state: record.state,
    failureCategory: record.failureCategory,
    resultJson,
    resultError,
    updatedAt: record.updatedAt,
  }
}
