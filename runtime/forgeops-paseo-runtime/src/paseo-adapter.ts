import { createPaseoClient, type PaseoClient } from '@getpaseo/client'
import { DaemonClient } from '@getpaseo/client/internal/daemon-client'
import { createHash } from 'node:crypto'
import type { PaseoAdapter, PaseoAgentSnapshot, ExecutionRequest } from './types.js'

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
    const submitted = await this.withConnectionGuard(this.client.agents.create({
      config: { provider: this.config.provider },
      cwd: input.cwd,
      prompt: input.prompt,
      clientMessageId: input.idempotencyKey,
      outputSchema: input.outputSchema,
      title: `ForgeOps ${input.role}`,
      worktree: {
        mode: 'branch-off',
        newBranch: worktreeBranch(input),
      },
    }))
    const agent = submitted.current() ?? await submitted.refresh().then((value) => value?.agent ?? null)
    return snapshot(agent, submitted.id)
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

function snapshot(agent: { id: string; status: string | null; lastError?: string | { message?: string } | null } | null, fallbackId: string): PaseoAgentSnapshot {
  if (!agent) return { id: fallbackId, status: null }
  return {
    id: agent.id,
    status: agent.status,
    lastError: typeof agent.lastError === 'string' ? { message: agent.lastError } : agent.lastError,
  }
}
