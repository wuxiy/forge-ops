import { FeedbackCore } from '@forgeops/feedback-core'
import type { ForgeOpsOptions } from '@forgeops/feedback-core'
import { mountWidget } from './widget'
import type { MountedWidget } from './widget'

export type { MountedWidget }
export { mountWidget }

export interface ForgeOpsFeedbackHandle {
  core: FeedbackCore
  widget: MountedWidget | null
  open(): void
  close(): void
  destroy(): void
}

/**
 * 一行接入（任意框架 / 无框架）：初始化采集并自动在 body 挂载反馈入口。
 *
 * initForgeOpsFeedback({ gatewayUrl, projectId, environment: 'test' })
 */
export function initForgeOpsFeedback(options: ForgeOpsOptions, target?: HTMLElement): ForgeOpsFeedbackHandle {
  const core = new FeedbackCore(options)
  core.start()
  let widget: MountedWidget | null = null
  if (core.enabled) {
    const host = target ?? document.body.appendChild(document.createElement('div'))
    widget = mountWidget(host, core)
  }
  return {
    core,
    widget,
    open: () => widget?.open(),
    close: () => widget?.close(),
    destroy: () => {
      widget?.destroy()
      widget = null
    },
  }
}
