import { createPaseoClient, type PaseoClient } from '@getpaseo/client'
import { DaemonClient } from '@getpaseo/client/internal/daemon-client'
import { createHash } from 'node:crypto'
import { isVerificationFamily, type PaseoAdapter, type PaseoAgentSnapshot, type ExecutionRequest } from './types.js'

/** The only production adapter. Paseo remains an executor; this service owns request idempotency. */
export class PaseoSdkAdapter implements PaseoAdapter {
  private readonly client: PaseoClient
  private readonly daemon: DaemonClient
  private connected = false

  constructor(private readonly config: { url: string; password?: string; provider: string }) {
    this.client = createPaseoClient({ url: config.url, password: config.password, reconnect: { enabled: true } })
    this.daemon = new DaemonClient({
      url: config.url,
      password: config.password,
      clientId: 'forgeops-v2-runtime-cancel',
      clientType: 'hub',
    })
  }

  async create(input: ExecutionRequest): Promise<PaseoAgentSnapshot> {
    await this.connect()
    // AGT-04 durable idempotency: the daemon does not deduplicate by client message id, so a
    // resubmission after a lost response first recovers the already-created agent by its
    // idempotency-key-bearing title instead of creating a second provider run.
    const title = agentTitle(input)
    const recovered = await this.findByTitle(title)
    if (recovered) return snapshot(recovered, recovered.id)
    const request: Record<string, unknown> = {
      config: { provider: this.config.provider },
      cwd: input.cwd,
      prompt: input.prompt,
      clientMessageId: input.idempotencyKey,
      outputSchema: input.outputSchema,
      title,
    }
    if (!isVerificationFamily(input.role)) {
      // Triage/Coding work on a branch-off worktree of the project repository (AGT-10);
      // verification-family roles run read-only in isolated task directories without one (VER-07).
      request.worktree = { mode: 'branch-off', newBranch: worktreeBranch(input) }
    }
    const submitted = await this.client.agents.create(request as unknown as Parameters<typeof this.client.agents.create>[0])
    const agent = submitted.current() ?? await submitted.refresh().then((value) => value?.agent ?? null)
    return snapshot(agent, submitted.id)
  }

  private async findByTitle(title: string): Promise<{ id: string; status: string | null; lastError?: string | { message?: string } | null } | null> {
    try {
      const list = await this.client.agents.list()
      // The daemon carries the title as `name`; the SDK type does not expose it yet.
      const entry = (list?.entries ?? []).find((item) => (item?.agent as unknown as { name?: string } | null)?.name === title)
      return (entry?.agent as unknown as { id: string; status: string | null; lastError?: string | { message?: string } | null }) ?? null
    } catch {
      return null
    }
  }

  async inspect(providerRunId: string): Promise<PaseoAgentSnapshot | null> {
    await this.connect()
    const agent = this.client.agents.ref(providerRunId)
    const result = await agent.refresh()
    if (!result) return null
    const current = snapshot(result.agent, providerRunId)
    if (current.status !== 'idle' && current.status !== 'closed') return current
    try {
      const timeline = await agent.timeline.refetch({ direction: 'tail', limit: 200, projection: 'canonical' })
      if (timeline.error) return { ...current, resultError: 'PASEO_TIMELINE_UNAVAILABLE' }
      const output = latestAssistantMessage(timeline.entries)
      if (!output) return { ...current, resultError: 'PASEO_OUTPUT_MISSING' }
      const parsed = JSON.parse(output)
      if (parsed === null || Array.isArray(parsed) || typeof parsed !== 'object') {
        return { ...current, resultError: 'PASEO_OUTPUT_NOT_OBJECT' }
      }
      return { ...current, resultJson: JSON.stringify(parsed) }
    } catch {
      return { ...current, resultError: 'PASEO_OUTPUT_UNAVAILABLE' }
    }
  }

  async cancel(providerRunId: string): Promise<void> {
    await this.connect()
    await this.daemon.cancelAgent(providerRunId)
  }

  async close(): Promise<void> {
    await this.client.close()
    await this.daemon.close()
    this.connected = false
  }

  private async connect(): Promise<void> {
    if (this.connected) return
    await this.withConnectionGuard(this.client.connect())
    await this.withConnectionGuard(this.daemon.connect())
    this.connected = true
  }

  /**
   * AGT-09: a daemon that is down must surface as a bounded failure, never an indefinite hang.
   * The service layer turns the rejection into a durable FAILED/PASEO_SUBMIT_FAILED fact.
   */
  private async withConnectionGuard<T>(operation: Promise<T>, timeoutMs = 5_000): Promise<T> {
    let timer: NodeJS.Timeout | undefined
    try {
      return await Promise.race([
        operation,
        new Promise<never>((_, reject) => {
          timer = setTimeout(() => reject(new Error('PASEO_CONNECT_TIMEOUT')), timeoutMs)
        }),
      ])
    } finally {
      clearTimeout(timer)
    }
  }
}

function latestAssistantMessage(entries: Array<{ item: { type: string; text?: string } }>): string | null {
  const chunks: string[] = []
  for (let index = entries.length - 1; index >= 0; index -= 1) {
    const item = entries[index].item
    if (item.type === 'assistant_message') {
      chunks.push(item.text ?? '')
    } else if (chunks.length) {
      break
    }
  }
  return chunks.length ? chunks.reverse().join('') : null
}

function worktreeBranch(input: ExecutionRequest): string {
  const suffix = createHash('sha256').update(input.idempotencyKey).digest('hex').slice(0, 12)
  return `forgeops/v2-${input.role.toLowerCase()}-${suffix}`
}

function agentTitle(input: ExecutionRequest): string {
  return `ForgeOps ${input.role} ${input.idempotencyKey}`
}

function snapshot(agent: { id: string; status: string | null; lastError?: string | { message?: string } | null } | null, fallbackId: string): PaseoAgentSnapshot {
  if (!agent) return { id: fallbackId, status: null }
  return {
    id: agent.id,
    status: agent.status,
    lastError: typeof agent.lastError === 'string' ? { message: agent.lastError } : agent.lastError,
  }
}
