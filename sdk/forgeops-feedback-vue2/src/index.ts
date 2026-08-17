import { FeedbackCore } from '@forgeops/feedback-core'
import type { ForgeOpsOptions } from '@forgeops/feedback-core'
import { initForgeOpsFeedback } from '@forgeops/feedback-dom'
import type { ForgeOpsFeedbackHandle } from '@forgeops/feedback-dom'

export * from '@forgeops/feedback-core'
export type { ForgeOpsFeedbackHandle } from '@forgeops/feedback-dom'

/** Vue 2 插件形状（install(Vue)）。 */
export interface Vue2Plugin {
  install(Vue: unknown): void
}

/**
 * Vue 2 接入（main.js）：
 *
 *   import { createForgeOps } from '@forgeops/feedback-vue2'
 *   Vue.use(createForgeOps({ gatewayUrl, projectId, environment: 'test' }))
 *
 * use 时自动挂载反馈入口（body），业务代码零改动。
 */
export function createForgeOps(options: ForgeOpsOptions): Vue2Plugin & { handle?: ForgeOpsFeedbackHandle } {
  const core = new FeedbackCore(options)
  const plugin: Vue2Plugin & { handle?: ForgeOpsFeedbackHandle } = {
    install(Vue: unknown) {
      core.start()
      if (core.enabled && typeof document !== 'undefined') {
        plugin.handle = initForgeOpsFeedback(options)
      }
      const proto = (Vue as { prototype?: Record<string, unknown> }).prototype
      if (proto) proto.$forgeops = plugin.handle
    },
  }
  return plugin
}
