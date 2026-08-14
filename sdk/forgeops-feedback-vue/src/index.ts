import { App, inject, reactive } from 'vue'
import ForgeOpsWidget from './ui/ForgeOpsWidget.vue'
import { RequestContextCollector } from './collector'
import { ForgeOpsGatewayClient } from './gateway'
import type { ForgeOpsOptions } from './types'

export * from './types'
export { RequestContextCollector, ForgeOpsGatewayClient }

export interface ForgeOpsContext {
  options: ForgeOpsOptions
  collector: RequestContextCollector
  client: ForgeOpsGatewayClient
  /** 上次提交人（我的反馈默认查询者） */
  lastReporter: { name: string }
  getReporter(): { id?: string; name: string } | null
}

const FORGEOPS_KEY = Symbol('forgeops')

export function createForgeOps(options: ForgeOpsOptions) {
  const enabledEnvironments = options.enabledEnvironments ?? ['test', 'uat', 'staging']
  const enabled = enabledEnvironments.includes(options.environment)

  const collector = new RequestContextCollector(options.requestBufferSize ?? 50)
  const client = new ForgeOpsGatewayClient(options.gatewayUrl)
  const state = reactive({ lastReporterName: '' })

  const context: ForgeOpsContext = {
    options,
    collector,
    client,
    get lastReporter() {
      return { name: state.lastReporterName }
    },
    getReporter() {
      const fromApp = options.getReporter?.()
      if (fromApp?.name) return fromApp
      return state.lastReporterName ? { name: state.lastReporterName } : null
    },
  }

  const plugin = {
    install(app: App) {
      if (!enabled) return
      app.provide(FORGEOPS_KEY, context)
      app.component('ForgeOpsWidget', ForgeOpsWidget)
      collector.start()
    },
  }

  return { plugin, context, enabled }
}

export function useForgeOps(): ForgeOpsContext {
  const ctx = inject<ForgeOpsContext>(FORGEOPS_KEY)
  if (!ctx) throw new Error('[ForgeOps] plugin not installed: app.use(createForgeOps(...).plugin)')
  return ctx
}

export { FORGEOPS_KEY }
