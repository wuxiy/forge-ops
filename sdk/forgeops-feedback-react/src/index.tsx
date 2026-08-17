import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import { FeedbackCore } from '@forgeops/feedback-core'
import type { ForgeOpsOptions } from '@forgeops/feedback-core'
import { initForgeOpsFeedback, mountWidget } from '@forgeops/feedback-dom'
import type { ForgeOpsFeedbackHandle } from '@forgeops/feedback-dom'

export * from '@forgeops/feedback-core'
export type { ForgeOpsFeedbackHandle } from '@forgeops/feedback-dom'

/**
 * 方式一（推荐，main.tsx 一行）：
 *
 *   initForgeOpsFeedback({ gatewayUrl, projectId, environment: 'test' })  // 直接用 DOM 包，等价
 *
 * 方式二：App 内挂组件（随组件生命周期自动创建/销毁）。
 */
export function ForgeOpsFeedback({ options, children }: { options: ForgeOpsOptions; children?: ReactNode }) {
  const hostRef = useRef<HTMLDivElement>(null)
  const coreRef = useRef<FeedbackCore | null>(null)

  useEffect(() => {
    if (!hostRef.current) return
    const core = new FeedbackCore(options)
    core.start()
    coreRef.current = core
    const widget = core.enabled ? mountWidget(hostRef.current, core) : null
    return () => {
      widget?.destroy()
      coreRef.current = null
    }
    // options 由调用方保证稳定（模块级常量），避免重复挂载
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div ref={hostRef}>
      {children}
    </div>
  )
}

/** Hook：拿到 core（采集器/客户端）用于自定义交互；入口仍由 initForgeOpsFeedback 或组件挂载。 */
export function useForgeOpsCore(options: ForgeOpsOptions): FeedbackCore | null {
  const ref = useRef<FeedbackCore | null>(null)
  if (ref.current === null) {
    ref.current = new FeedbackCore(options)
  }
  useEffect(() => {
    ref.current?.start()
  }, [])
  return ref.current
}

export { initForgeOpsFeedback }
