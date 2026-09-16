import { resolve } from 'node:path'

export interface RuntimeConfig {
  host: string
  port: number
  serviceToken: string
  dataFile: string
  paseoUrl: string
  paseoPassword?: string
  provider: string
  allowedRoots: string[]
  taskRoots: string[]
  defaultRunTimeoutMs: number
  maxConcurrentPerProject: number
}

export function loadConfig(environment = process.env): RuntimeConfig {
  const serviceToken = required(environment.FORGEOPS_RUNTIME_SERVICE_TOKEN, 'FORGEOPS_RUNTIME_SERVICE_TOKEN')
  const dataFile = required(environment.FORGEOPS_RUNTIME_DATA_FILE, 'FORGEOPS_RUNTIME_DATA_FILE')
  const allowedRoots = required(environment.FORGEOPS_RUNTIME_ALLOWED_ROOTS, 'FORGEOPS_RUNTIME_ALLOWED_ROOTS')
    .split(',').map((value) => resolve(value.trim())).filter(Boolean)
  if (!allowedRoots.length) throw new Error('FORGEOPS_RUNTIME_ALLOWED_ROOTS must contain at least one path')
  const taskRoots = (environment.FORGEOPS_RUNTIME_TASK_ROOTS ?? '')
    .split(',').map((value) => resolve(value.trim())).filter(Boolean)
  const host = environment.FORGEOPS_RUNTIME_HOST ?? '127.0.0.1'
  if (host !== '127.0.0.1' && host !== '::1') throw new Error('Runtime must bind loopback until a private-network deployment is verified')
  const port = Number(environment.FORGEOPS_RUNTIME_PORT ?? '7676')
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('FORGEOPS_RUNTIME_PORT is invalid')
  const defaultRunTimeoutMs = Number(environment.FORGEOPS_RUNTIME_RUN_TIMEOUT_MS ?? '900000')
  if (!Number.isInteger(defaultRunTimeoutMs) || defaultRunTimeoutMs < 1 || defaultRunTimeoutMs > 86_400_000) {
    throw new Error('FORGEOPS_RUNTIME_RUN_TIMEOUT_MS must be between 1 and 86400000')
  }
  const maxConcurrentPerProject = Number(environment.FORGEOPS_RUNTIME_MAX_CONCURRENT_PER_PROJECT ?? '1')
  if (!Number.isInteger(maxConcurrentPerProject) || maxConcurrentPerProject < 1 || maxConcurrentPerProject > 100) {
    throw new Error('FORGEOPS_RUNTIME_MAX_CONCURRENT_PER_PROJECT must be between 1 and 100')
  }
  return {
    host,
    port,
    serviceToken,
    dataFile: resolve(dataFile),
    paseoUrl: environment.FORGEOPS_PASEO_URL ?? 'ws://127.0.0.1:6767/ws',
    paseoPassword: environment.FORGEOPS_PASEO_PASSWORD,
    provider: environment.FORGEOPS_PASEO_PROVIDER ?? 'codex/gpt-5.5',
    allowedRoots,
    taskRoots,
    defaultRunTimeoutMs,
    maxConcurrentPerProject,
  }
}

function required(value: string | undefined, name: string): string {
  if (!value?.trim()) throw new Error(`${name} is required`)
  return value
}
