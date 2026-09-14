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
    const agent = await this.client.agents.create({
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
    })
    return snapshot(agent.current() ?? await agent.refresh().then((value) => value?.agent ?? null), agent.id)
  }

  async inspect(providerRunId: string): Promise<PaseoAgentSnapshot | null> {
    await this.connect()
    const result = await this.client.agents.ref(providerRunId).refresh()
    return result ? snapshot(result.agent, providerRunId) : null
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
    await this.client.connect()
    await this.daemon.connect()
    this.connected = true
  }
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
