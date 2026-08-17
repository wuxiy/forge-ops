import type { App } from 'vue'
import { defineComponent } from 'vue'
import { FeedbackCore } from '@forgeops/feedback-core'
import type { ForgeOpsOptions } from '@forgeops/feedback-core'
import { initForgeOpsFeedback } from '@forgeops/feedback-dom'
import type { ForgeOpsFeedbackHandle } from '@forgeops/feedback-dom'

export * from '@forgeops/feedback-core'
export type { ForgeOpsFeedbackHandle } from '@forgeops/feedback-dom'

const FORGEOPS_KEY = Symbol('forgeops')

/** 兼容旧版（v0.1 SFC 版）的上下文形状。 */
export interface ForgeOpsContext {
  options: ForgeOpsOptions
  collector: import('@forgeops/feedback-core').RequestContextCollector
  client: import('@forgeops/feedback-core').ForgeOpsGatewayClient
  lastReporter: { name: string }
  getReporter(): { id?: string; name: string } | null
}

export function createForgeOps(options: ForgeOpsOptions) {
  const core = new FeedbackCore(options)
  let handle: ForgeOpsFeedbackHandle | null = null
  const lastReporter = {
    get name() {
      return core.lastReporterName
    },
    set name(v: string) {
      core.setReporterName(v)
    },
  }
  const context: ForgeOpsContext = {
    options,
    collector: core.collector,
    client: core.client,
    lastReporter,
    getReporter: () => core.getReporter(),
  }

  const plugin = {
    install(app: App) {
      core.start()
      if (core.enabled && typeof document !== 'undefined') {
        handle = initForgeOpsFeedback(options)
      }
      app.provide(FORGEOPS_KEY, { core, handle })
    },
  }

  return { plugin, context, enabled: core.enabled, open: () => handle?.open(), close: () => handle?.close() }
}

/**
 * 兼容占位组件：v0.2 起 UI 由 install 自动挂载（body），
 * 旧项目的 <ForgeOpsWidget /> 无需移除，渲染为空节点。
 */
export const ForgeOpsWidget = defineComponent({
  name: 'ForgeOpsWidget',
  setup() {
    return () => null
  },
})

export { FORGEOPS_KEY }
