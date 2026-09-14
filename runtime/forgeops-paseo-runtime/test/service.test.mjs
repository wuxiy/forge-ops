import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { startServer } from '../dist/server.js'
import { loadConfig } from '../dist/config.js'

class FakePaseo {
  creations = 0
  cancellations = 0
  closed = false
  agents = new Map()

  async create() {
    this.creations += 1
    const id = `paseo-${this.creations}`
    this.agents.set(id, { id, status: 'running' })
    return this.agents.get(id)
  }

  async inspect(id) {
    const agent = this.agents.get(id)
    return agent ? { ...agent, resultJson: '{"classification":"NO_CODE_REQUIRED"}' } : null
  }

  async cancel(id) {
    this.cancellations += 1
    this.agents.set(id, { id, status: 'cancelled' })
  }

  async close() {
    this.closed = true
  }
}

const root = await mkdtemp(join(tmpdir(), 'forgeops-runtime-test-'))
after(() => rm(root, { recursive: true, force: true }))

function config(dataFile, port = 0) {
  return {
    host: '127.0.0.1',
    port,
    serviceToken: 'runtime-test-token',
    dataFile,
    paseoUrl: 'ws://127.0.0.1:17677/ws',
    provider: 'codex/gpt-5.5',
    allowedRoots: [root],
  }
}

function payload(key = 'evt-1', cwd = root) {
  return {
    idempotencyKey: key,
    projectId: 'pilot-project',
    role: 'TRIAGE',
    cwd,
    prompt: 'Return one concise classification.',
    outputSchema: { type: 'object', required: ['classification'] },
  }
}

async function request(baseUrl, path, options = {}) {
  const headers = { authorization: 'Bearer runtime-test-token', ...(options.headers ?? {}) }
  return fetch(`${baseUrl}${path}`, { ...options, headers })
}

test('requires a token, keeps one execution per idempotency key, and survives restart', async () => {
  const dataFile = join(root, 'runs.json')
  const firstAdapter = new FakePaseo()
  const first = await startServer(config(dataFile), firstAdapter)
  const address = first.server.address()
  assert.equal(typeof address, 'object')
  const baseUrl = `http://127.0.0.1:${address.port}`

  const unauthorized = await fetch(`${baseUrl}/v1/runs`)
  assert.equal(unauthorized.status, 401)

  const submitted = await Promise.all(Array.from({ length: 10 }, () => request(baseUrl, '/v1/runs', {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(payload()),
  })))
  assert.deepEqual(submitted.map((response) => response.status), Array(10).fill(202))
  const snapshots = await Promise.all(submitted.map((response) => response.json()))
  assert.equal(firstAdapter.creations, 1)
  assert.deepEqual(new Set(snapshots.map((snapshot) => snapshot.providerRunId)), new Set(['paseo-1']))
  await first.close()
  assert.equal(firstAdapter.closed, true)

  const secondAdapter = new FakePaseo()
  const second = await startServer(config(dataFile), secondAdapter)
  const secondAddress = second.server.address()
  assert.equal(typeof secondAddress, 'object')
  const secondUrl = `http://127.0.0.1:${secondAddress.port}`
  const duplicate = await request(secondUrl, '/v1/runs', {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(payload()),
  })
  assert.equal(duplicate.status, 202)
  assert.deepEqual(await duplicate.json(), snapshots[0])
  assert.equal(secondAdapter.creations, 0)

  secondAdapter.agents.set('paseo-1', { id: 'paseo-1', status: 'idle' })
  const completed = await request(secondUrl, '/v1/runs/evt-1')
  assert.equal(completed.status, 200)
  const completedSnapshot = await completed.json()
  assert.equal(completedSnapshot.state, 'SUCCEEDED')
  assert.equal(completedSnapshot.resultJson, '{"classification":"NO_CODE_REQUIRED"}')

  const cancelSubmit = await request(secondUrl, '/v1/runs', {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(payload('evt-cancel')),
  })
  assert.equal(cancelSubmit.status, 202)
  const cancelled = await request(secondUrl, '/v1/runs/evt-cancel', { method: 'POST' })
  assert.equal(cancelled.status, 200)
  assert.equal((await cancelled.json()).state, 'CANCELLED')
  assert.equal(secondAdapter.cancellations, 1)
  const absent = await request(secondUrl, '/v1/runs/not-found')
  assert.equal(absent.status, 404)
  await second.close()
})

test('rejects non-loopback configuration and paths outside the project allowlist', async () => {
  assert.throws(() => loadConfig({
    FORGEOPS_RUNTIME_SERVICE_TOKEN: 'token',
    FORGEOPS_RUNTIME_DATA_FILE: join(root, 'config.json'),
    FORGEOPS_RUNTIME_ALLOWED_ROOTS: root,
    FORGEOPS_RUNTIME_HOST: '0.0.0.0',
  }), /loopback/)

  const adapter = new FakePaseo()
  const runtime = await startServer(config(join(root, 'invalid-path.json')), adapter)
  const address = runtime.server.address()
  assert.equal(typeof address, 'object')
  const response = await request(`http://127.0.0.1:${address.port}`, '/v1/runs', {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(payload('evt-invalid', '/tmp/outside-project')),
  })
  assert.equal(response.status, 400)
  assert.equal(adapter.creations, 0)
  await runtime.close()
})
