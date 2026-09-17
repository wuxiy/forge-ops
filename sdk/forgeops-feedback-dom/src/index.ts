import { FeedbackCore } from '@forgeops/feedback-core'
import type { ForgeOpsOptions } from '@forgeops/feedback-core'
import { mountWidget } from './widget'
import type { MountedWidget } from './widget'

export type { MountedWidget }
export { mountWidget }

export interface ForgeOpsFeedbackHandle {
  core: FeedbackCore
  open(): void
  close(): void
  destroy(): void
}

const HANDLE_KEY = '__forgeopsV2Handle__'

/**
 * The single 2.0 initialization entrypoint. Repeated initialization returns the existing handle;
 * destroy restores browser hooks and removes the only DOM mount.
 */
export function initForgeOpsFeedback(options: ForgeOpsOptions, target?: HTMLElement): ForgeOpsFeedbackHandle {
  const root = window as Window & { [HANDLE_KEY]?: ForgeOpsFeedbackHandle }
  if (root[HANDLE_KEY]) return root[HANDLE_KEY]!
  const core = new FeedbackCore(options)
  core.start()
  const host = target ?? document.body.appendChild(document.createElement('div'))
  const createdHost = target === undefined
  const widget: MountedWidget | null = core.enabled ? mountWidget(host, core) : null
  const handle: ForgeOpsFeedbackHandle = {
    core,
    open: () => widget?.open(),
    close: () => widget?.close(),
    destroy: () => {
      widget?.destroy()
      core.stop()
      if (createdHost) host.remove()
      delete root[HANDLE_KEY]
    },
  }
  root[HANDLE_KEY] = handle
  return handle
}
