import { createServer, type IncomingMessage, type ServerResponse } from 'node:http'
import { timingSafeEqual } from 'node:crypto'
import { loadConfig, type RuntimeConfig } from './config.js'
import { PaseoSdkAdapter } from './paseo-adapter.js'
import { ExecutionService } from './service.js'
import { RunStore } from './store.js'
import type { ExecutionRequest, PaseoAdapter } from './types.js'

export async function startServer(config = loadConfig(), adapter: PaseoAdapter = new PaseoSdkAdapter({ url: config.paseoUrl, password: config.paseoPassword, provider: config.provider })) {
  const store = new RunStore(config.dataFile)
  await store.load()
  const service = new ExecutionService(config, store, adapter)
  const server = createServer((request, response) => void handle(request, response, config, service))
  await new Promise<void>((resolve, reject) => server.once('error', reject).listen(config.port, config.host, resolve))
  return { server, service, close: async () => { await adapter.close(); await new Promise<void>((resolve, reject) => server.close((error) => error ? reject(error) : resolve())) } }
}

async function handle(request: IncomingMessage, response: ServerResponse, config: RuntimeConfig, service: ExecutionService): Promise<void> {
  if (!authorized(request, config.serviceToken)) return send(response, 401, { code: 'UNAUTHORIZED' })
  try {
    if (request.method === 'POST' && request.url === '/v1/runs') return send(response, 202, await service.submit(await body(request) as ExecutionRequest))
    const match = request.url?.match(/^\/v1\/runs\/([^/]+)$/)
    if (match && request.method === 'GET') {
      const snapshot = await service.inspect(decodeURIComponent(match[1]))
      return snapshot ? send(response, 200, snapshot) : send(response, 404, { code: 'NOT_FOUND' })
    }
    if (match && request.method === 'POST') {
      const snapshot = await service.cancel(decodeURIComponent(match[1]))
      return snapshot ? send(response, 200, snapshot) : send(response, 404, { code: 'NOT_FOUND' })
    }
    return send(response, 404, { code: 'NOT_FOUND' })
  } catch (error) {
    return send(response, 400, { code: 'INVALID_REQUEST', message: error instanceof Error ? error.message : 'invalid request' })
  }
}

function authorized(request: IncomingMessage, expected: string): boolean {
  const received = request.headers.authorization
  if (!received?.startsWith('Bearer ')) return false
  const candidate = Buffer.from(received.substring(7))
  const secret = Buffer.from(expected)
  return candidate.length === secret.length && timingSafeEqual(candidate, secret)
}

async function body(request: IncomingMessage): Promise<unknown> {
  let content = ''
  for await (const chunk of request) {
    content += chunk
    if (content.length > 131072) throw new Error('request body too large')
  }
  return JSON.parse(content)
}

function send(response: ServerResponse, status: number, value: unknown): void {
  response.writeHead(status, { 'content-type': 'application/json' })
  response.end(JSON.stringify(value))
}

if (process.argv[1]?.endsWith('/server.js')) {
  startServer().then(({ server }) => server.on('error', (error) => { throw error }))
}
