import assert from 'node:assert/strict'
import test from 'node:test'
import { RequestContextCollector } from '../dist/collector.js'

test('start is idempotent and stop restores fetch and console.error', async () => {
  const originalWindow = globalThis.window
  const originalFetch = async () => new Response('', { status: 500 })
  const originalConsoleError = console.error
  globalThis.window = { fetch: originalFetch }

  try {
    const collector = new RequestContextCollector()
    collector.start()
    const wrappedFetch = window.fetch
    collector.start()
    assert.equal(window.fetch, wrappedFetch)

    await window.fetch('https://example.test/failure')
    assert.equal(collector.failedRequests().length, 1)
    assert.equal(collector.failedRequests()[0].status, 500)

    collector.stop()
    assert.equal(window.fetch, originalFetch)
    assert.equal(console.error, originalConsoleError)
  } finally {
    if (originalWindow === undefined) delete globalThis.window
    else globalThis.window = originalWindow
    console.error = originalConsoleError
  }
})
