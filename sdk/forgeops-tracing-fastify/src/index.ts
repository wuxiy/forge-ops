/**
 * ForgeOps Request-ID Tracing - Fastify 5 插件（零依赖单文件）
 *
 * 与 forgeops-spring-boot-starter 等价能力：
 *  - X-Request-ID 透传/生成（ULID，与前端 SDK 一致）+ 响应回显
 *  - 结构化 JSON 访问日志（pino 的 ACCESS logger 或 stdout）
 *  - GET /api/version                     -> {service, version, commit}
 *  - GET /api/admin/requests/glm-5.3_common  -> ForgeOps Gateway Context Pack 日志摘录入口
 *
 * 用法（server/src/index.ts）：
 *   import { forgeopsTracing } from './forgeops/tracing.js'
 *   await app.register(forgeopsTracing, { serviceName: 'akso-agent-databot' })
 *
 * 可选项：
 *   serviceName        日志/版本端点中的服务名（默认 'unknown-app'）
 *   version / commit   版本信息（默认读 package.json / env）
 *   exposeRequestLogs  是否暴露日志查询端点（默认 true）
 *   trackedRequests    内存缓冲条数（默认 500）
 *   logger             可选 pino 实例；缺省用 console
 */
import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify'

export interface ForgeopsTracingOptions {
  serviceName?: string
  version?: string
  commit?: string
  exposeRequestLogs?: boolean
  trackedRequests?: number
  logger?: { info: (obj: object, msg?: string) => void }
}

const ULID_ENC = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'
const RANDOM = globalThis.crypto ?? (await import('node:crypto')).webcrypto

function ulid(): string {
  let time = Date.now()
  let out = ''
  for (let i = 0; i < 10; i++) {
    out = ULID_ENC[time % 32] + out
    time = Math.floor(time / 32)
  }
  const bytes = new Uint8Array(10)
  RANDOM.getRandomValues(bytes)
  let entropy = ''
  for (const b of bytes) entropy += ULID_ENC[b % 32]
  return out + entropy
}

function jsonEscape(s: string): string {
  return s
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"')
    .replace(/\n/g, '\\n')
    .replace(/\r/g, '\\r')
    .replace(/\t/g, '\\t')
}

interface LogEntry {
  [k: string]: string | number
}

/** 内存请求日志缓冲（按 requestId 归档，LRU 淘汰）。 */
export class RequestLogStore {
  private readonly map = new Map<string, LogEntry[]>()
  constructor(private readonly max = 500, private readonly perRequestMax = 50) {}

  append(requestId: string, entry: LogEntry): void {
    const list = this.map.get(requestId) ?? []
    if (list.length >= this.perRequestMax) list.shift()
    list.push(entry)
    this.map.set(requestId, list)
    if (this.map.size > this.max) {
      // Map 迭代序即插入序：删最旧的 key
      const oldest = this.map.keys().next().value
      if (oldest !== undefined) this.map.delete(oldest)
    }
  }

  find(requestId: string): LogEntry[] {
    return this.map.get(requestId) ?? []
  }
}

/**
 * 直接调用形式（推荐）：在 app 根作用域调用，hooks 对**全部路由**生效。
 *
 *   import { setupForgeopsTracing } from './forgeops/tracing.js'
 *   await setupForgeopsTracing(app, { serviceName: 'xxx' })
 *
 * 注意：Fastify 插件默认封装（encapsulation），经 app.register(forgeopsTracing) 注册时
 * hooks 只作用于插件 scope 内的路由——业务路由要全量覆盖必须用本函数直接调用。
 */
export const setupForgeopsTracing = async (app: FastifyInstance, opts: ForgeopsTracingOptions = {}) => {
  const serviceName = opts.serviceName ?? 'unknown-app'
  const version = opts.version ?? process.env.APP_VERSION ?? 'dev'
  const commit = opts.commit ?? process.env.APP_COMMIT ?? 'unknown'
  const exposeRequestLogs = opts.exposeRequestLogs ?? true
  const store = new RequestLogStore(opts.trackedRequests ?? 500)
  const logger = opts.logger ?? console

  // 1) Request-ID：透传或补生成 -> 回显响应头（onRequest 最先执行）
  app.addHook('onRequest', async (req: FastifyRequest, reply: FastifyReply) => {
    const rid = (req.headers['x-request-id'] as string | undefined)?.trim() || ulid()
    ;(req as any).forgeopsRequestId = rid
    reply.header('x-request-id', rid)
  })

  // 2) handler 异常捕获（onError 早于 onResponse，转存到请求上下文）
  app.addHook('onError', async (req: FastifyRequest, _reply: FastifyReply, error: Error) => {
    ;(req as any).forgeopsException = `${error.name}: ${error.message}`.slice(0, 300)
  })

  // 3) 结构化访问日志（onResponse：含状态与耗时）+ 写入内存 store
  app.addHook('onResponse', async (req: FastifyRequest, reply: FastifyReply) => {
    const rid = (req as any).forgeopsRequestId as string
    if (!rid) return
    const entry: LogEntry = {
      ts: new Date().toISOString(),
      requestId: rid,
      traceId: (req.headers['x-trace-id'] as string | undefined) ?? '',
      service: serviceName,
      uri: req.url,
      httpMethod: req.method,
      status: reply.statusCode,
      durationMs: Date.now() - (req as any).forgeopsStartAt,
      version,
      commitSha: commit,
      exception: ((req as any).forgeopsException as string) ?? '',
    }
    store.append(rid, entry)
    const line = Object.entries(entry)
      .map(([k, v]) => `"${k}":"${typeof v === 'number' ? v : jsonEscape(String(v))}"`)
      .join(',')
      .replace(/"(\d+)"/g, '$1') // 数字字段去引号
    logger.info({ ForgeOpsAccess: true }, `{${line}}`)
  })
  app.addHook('onRequest', async (req: FastifyRequest) => {
    ;(req as any).forgeopsStartAt = Date.now()
  })

  // 3) 版本端点（SDK getBackendInfo 消费）
  app.get('/api/version', async () => ({ service: serviceName, version, commit }))

  // 4) 按 requestId 查日志（ForgeOps Gateway Context Pack 拉取入口）
  if (exposeRequestLogs) {
    app.get('/api/admin/requests/:requestId', async (req, reply) => {
      const { requestId } = req.params as { requestId: string }
      const entries = store.find(requestId)
      return { requestId, entries, count: entries.length }
    })
  }
}

/** register 形式（仅插件 scope 内路由生效；业务全量覆盖请用 setupForgeopsTracing 直接调用）。 */
export const forgeopsTracing = setupForgeopsTracing

export default forgeopsTracing
