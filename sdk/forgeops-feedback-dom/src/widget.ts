import type { FeedbackCore } from '@forgeops/feedback-core'
import type { FeedbackDetail, FeedbackListItem, FeedbackSubmission } from '@forgeops/feedback-core'
import { WIDGET_CSS } from './style'

export interface MountedWidget {
  open(): void
  close(): void
  destroy(): void
}

interface WidgetState {
  open: boolean
  tab: 'submit' | 'mine'
  detail: FeedbackDetail | null
  form: {
    type: FeedbackSubmission['type']
    title: string
    description: string
    expectedBehavior: string
    steps: string
    note: string
    reporterName: string
  }
  submitting: boolean
  error: string
  successId: string
  mineReporter: string
  mineList: FeedbackListItem[]
  mineError: string
  verifyNote: string
  verifyError: string
}

function esc(s: unknown): string {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function statusClass(display: string): string {
  if (display === '已完成') return 'forgeops-st-done'
  if (display === '待验证') return 'forgeops-st-verify'
  if (display === '需要补充') return 'forgeops-st-info'
  if (display === '待处理') return 'forgeops-st-todo'
  return 'forgeops-st-doing'
}

function fmtTime(iso: string): string {
  return iso ? iso.replace('T', ' ').slice(0, 19) : ''
}

/**
 * 挂载 ForgeOps 反馈 Widget（原生 DOM，无框架依赖）。
 * 返回 { open, close, destroy }；样式与事件完全自包含。
 */
export function mountWidget(container: HTMLElement, core: FeedbackCore): MountedWidget {
  container.className = 'forgeops-root'
  container.innerHTML = ''

  const state: WidgetState = {
    open: false,
    tab: 'submit',
    detail: null,
    form: { type: 'BUG', title: '', description: '', expectedBehavior: '', steps: '', note: '', reporterName: '' },
    submitting: false,
    error: '',
    successId: '',
    mineReporter: core.lastReporterName,
    mineList: [],
    mineError: '',
    verifyNote: '',
    verifyError: '',
  }

  const styleId = 'forgeops-widget-style'
  if (!document.getElementById(styleId)) {
    const style = document.createElement('style')
    style.id = styleId
    style.textContent = WIDGET_CSS
    document.head.appendChild(style)
  }

  const failedCount = () => core.collector.failedRequests().length
  const consoleCount = () => core.collector.consoleErrorList().length

  function render(): void {
    if (!state.open) {
      container.innerHTML = `<button type="button" class="forgeops-fab" data-act="open">反馈</button>`
      bindEvents()
      return
    }
    container.innerHTML = `
      <div class="forgeops-panel">
        <div class="forgeops-header">
          <span class="forgeops-title">ForgeOps 反馈</span>
          <button type="button" class="forgeops-close" data-act="close">✕</button>
        </div>
        <div class="forgeops-tabs">
          <button type="button" class="forgeops-tab ${state.tab === 'submit' ? 'active' : ''}" data-act="tab-submit">提交反馈</button>
          <button type="button" class="forgeops-tab ${state.tab === 'mine' ? 'active' : ''}" data-act="tab-mine">我的反馈</button>
        </div>
        <div class="forgeops-body">${state.tab === 'submit' ? renderSubmit() : renderMine()}</div>
      </div>`
    bindEvents()
  }

  function renderSubmit(): string {
    const f = state.form
    const preview: FeedbackSubmission = core.buildSubmission({ ...f })
    const { screenshot, ...rest } = preview
    const autoJson = esc(JSON.stringify({ ...rest, screenshot: undefined }, null, 2))
    return `
      <label class="forgeops-field">反馈类型
        <select data-field="type">
          <option value="BUG" ${f.type === 'BUG' ? 'selected' : ''}>Bug</option>
          <option value="OPTIMIZATION" ${f.type === 'OPTIMIZATION' ? 'selected' : ''}>优化建议</option>
          <option value="REQUIREMENT" ${f.type === 'REQUIREMENT' ? 'selected' : ''}>需求建议</option>
        </select>
      </label>
      <label class="forgeops-field">概要
        <input data-field="title" maxlength="120" placeholder="一句话概要（可选）" value="${esc(f.title)}">
      </label>
      <label class="forgeops-field">问题描述 <span class="forgeops-required">*</span>
        <textarea data-field="description" rows="3" maxlength="8000" placeholder="发生了什么？">${esc(f.description)}</textarea>
      </label>
      <label class="forgeops-field">预期效果
        <textarea data-field="expectedBehavior" rows="2" maxlength="4000" placeholder="期望的正确行为">${esc(f.expectedBehavior)}</textarea>
      </label>
      <label class="forgeops-field">操作步骤（可选）
        <textarea data-field="steps" rows="2" maxlength="4000" placeholder="复现步骤">${esc(f.steps)}</textarea>
      </label>
      <label class="forgeops-field">补充说明（可选）
        <input data-field="note" maxlength="4000" value="${esc(f.note)}">
      </label>
      <label class="forgeops-field">你的姓名 <span class="forgeops-required">*</span>
        <input data-field="reporterName" maxlength="64" placeholder="用于验证闭环通知" value="${esc(f.reporterName)}">
      </label>
      <details>
        <summary class="forgeops-details-toggle">自动附带上下文（${failedCount()} 条失败请求 · ${consoleCount()} 条控制台错误）</summary>
        <pre class="forgeops-pre">${autoJson}</pre>
      </details>
      ${state.error ? `<div class="forgeops-error">${esc(state.error)}</div>` : ''}
      ${state.successId ? `<div class="forgeops-success">已提交 ${esc(state.successId)}，可在「我的反馈」中跟踪进度。</div>` : ''}
      <button type="button" class="forgeops-submit" data-act="submit" ${state.submitting ? 'disabled' : ''}>
        ${state.submitting ? '提交中…' : '提交反馈'}
      </button>`
  }

  function renderMine(): string {
    if (state.detail) {
      const d = state.detail
      return `
        <button type="button" class="forgeops-back" data-act="back">← 返回列表</button>
        <div class="forgeops-detail-title">${esc(d.id)} · ${esc(d.title)}</div>
        <div><span class="forgeops-status ${statusClass(d.displayStatus)}">${esc(d.displayStatus)}</span></div>
        ${d.deploymentVersion ? `<div class="forgeops-meta">部署版本：<code>${esc(d.deploymentVersion)}</code></div>` : ''}
        ${d.prUrl ? `<div class="forgeops-meta">PR：<a href="${esc(d.prUrl)}" target="_blank" rel="noreferrer">${esc(d.prUrl)}</a></div>` : ''}
        ${d.multicaIssueUrl ? `<div class="forgeops-meta">Issue：<a href="${esc(d.multicaIssueUrl)}" target="_blank" rel="noreferrer">${esc(d.multicaIssueUrl)}</a></div>` : ''}
        <div class="forgeops-timeline">
          ${d.comments
            .map(
              (c) => `
            <div class="forgeops-comment">
              <div class="forgeops-comment-head"><span>${esc(c.author)}</span><span>${esc(fmtTime(c.createdAt))}</span></div>
              <div class="forgeops-comment-body">${esc(c.content)}</div>
            </div>`,
            )
            .join('')}
        </div>
        ${
          d.displayStatus === '待验证'
            ? `
        <label class="forgeops-field">验证说明（可选）
          <textarea data-field="verifyNote" rows="2" placeholder="验证说明">${esc(state.verifyNote)}</textarea>
        </label>
        <div class="forgeops-verify">
          <button type="button" class="forgeops-pass" data-act="verify-pass">验证通过</button>
          <button type="button" class="forgeops-fail" data-act="verify-fail">仍有问题</button>
        </div>`
            : ''
        }
        ${state.verifyError ? `<div class="forgeops-error">${esc(state.verifyError)}</div>` : ''}`
    }
    return `
      <div class="forgeops-row">
        <input data-field="mineReporter" maxlength="64" placeholder="你的姓名" value="${esc(state.mineReporter)}">
        <button type="button" data-act="load-mine">查询</button>
      </div>
      ${state.mineError ? `<div class="forgeops-error">${esc(state.mineError)}</div>` : ''}
      ${state.mineList
        .map(
          (i) => `
        <div class="forgeops-item" data-act="open-detail" data-id="${esc(i.id)}">
          <div class="forgeops-item-line">
            <span class="forgeops-item-id">${esc(i.id)}</span>
            <span class="forgeops-status ${statusClass(i.displayStatus)}">${esc(i.displayStatus)}</span>
          </div>
          <div>${esc(i.title)}</div>
        </div>`,
        )
        .join('')}
      ${!state.mineList.length && !state.mineError ? '<div class="forgeops-empty">暂无反馈记录</div>' : ''}`
  }

  function bindEvents(): void {
    container.querySelectorAll<HTMLElement>('[data-field]').forEach((el) => {
      const field = el.dataset.field
      if (!field) return
      const handler = () => {
        const value = (el as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement).value
        if (field === 'mineReporter') state.mineReporter = value
        else if (field === 'verifyNote') state.verifyNote = value
        else if (field in state.form) (state.form as Record<string, unknown>)[field] = value
        // 输入不重渲染，避免焦点丢失；选择框除外
        if (el.tagName === 'SELECT') render()
      }
      el.addEventListener('change', handler)
      if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
        el.addEventListener('input', handler)
      }
    })

    container.querySelectorAll<HTMLElement>('[data-act]').forEach((el) => {
      el.addEventListener('click', async (ev) => {
        const act = el.dataset.act
        try {
          switch (act) {
            case 'open':
              if (!state.form.reporterName) {
                const r = core.getReporter()
                state.form.reporterName = r?.name ?? state.form.reporterName
                state.mineReporter = state.mineReporter || state.form.reporterName
              }
              state.open = true
              break
            case 'close':
              state.open = false
              break
            case 'tab-submit':
              state.tab = 'submit'
              break
            case 'tab-mine':
              state.tab = 'mine'
              if (state.mineReporter) await loadMine()
              break
            case 'submit':
              await submit()
              break
            case 'load-mine':
              await loadMine()
              break
            case 'open-detail': {
              const id = (el.closest('[data-id]') as HTMLElement)?.dataset.id ?? el.dataset.id
              if (id) await openDetail(id)
              break
            }
            case 'back':
              state.detail = null
              break
            case 'verify-pass':
              await verify(true)
              break
            case 'verify-fail':
              await verify(false)
              break
          }
        } catch (e) {
          state.error = e instanceof Error ? e.message : String(e)
        }
        ev.stopPropagation()
        render()
      })
    })
  }

  async function submit(): Promise<void> {
    const f = state.form
    if (!f.description.trim() || !f.reporterName.trim()) {
      state.error = '请填写问题描述与你的姓名'
      return
    }
    state.error = ''
    state.successId = ''
    state.submitting = true
    render()
    try {
      const payload = core.buildSubmission(f)
      const result = await core.client.submitFeedback(payload)
      state.successId = result.id
      core.setReporterName(f.reporterName)
      state.mineReporter = f.reporterName
      state.form.description = ''
      state.form.title = ''
      state.form.expectedBehavior = ''
      state.form.steps = ''
      state.form.note = ''
    } catch (e) {
      state.error = e instanceof Error ? e.message : String(e)
    } finally {
      state.submitting = false
    }
  }

  async function loadMine(): Promise<void> {
    if (!state.mineReporter.trim()) return
    state.mineError = ''
    try {
      state.mineList = await core.client.listMyFeedback(state.mineReporter, core.options.projectId)
    } catch (e) {
      state.mineError = e instanceof Error ? e.message : String(e)
    }
  }

  async function openDetail(id: string): Promise<void> {
    state.verifyError = ''
    state.verifyNote = ''
    state.detail = await core.client.getFeedback(id)
  }

  async function verify(pass: boolean): Promise<void> {
    if (!state.detail) return
    state.verifyError = ''
    const verifier = core.getReporter()?.name || state.mineReporter || 'unknown'
    try {
      if (pass) {
        await core.client.verifyPass(state.detail.id, verifier, state.verifyNote || undefined)
      } else {
        if (!state.verifyNote.trim()) {
          state.verifyError = '请填写“仍有问题”的具体说明（验证说明必填）'
          return
        }
        await core.client.verifyFail(state.detail.id, verifier, state.verifyNote, {
          requests: core.collector.failedRequests(),
          consoleErrors: core.collector.consoleErrorList(),
        })
      }
      await openDetail(state.detail.id)
    } catch (e) {
      state.verifyError = e instanceof Error ? e.message : String(e)
    }
  }

  render()

  return {
    open() {
      state.open = true
      render()
    },
    close() {
      state.open = false
      render()
    },
    destroy() {
      container.innerHTML = ''
    },
  }
}
