<template>
  <div v-if="ctx" class="forgeops-root">
    <button v-if="!open" class="forgeops-fab" type="button" @click="open = true">反馈</button>

    <div v-if="open" class="forgeops-panel">
      <div class="forgeops-header">
        <span class="forgeops-title">ForgeOps 反馈</span>
        <button class="forgeops-close" type="button" @click="open = false">✕</button>
      </div>

      <div class="forgeops-tabs">
        <button :class="{ active: tab === 'submit' }" type="button" @click="tab = 'submit'">提交反馈</button>
        <button :class="{ active: tab === 'mine' }" type="button" @click="switchToMine">我的反馈</button>
      </div>

      <form v-if="tab === 'submit'" class="forgeops-body" @submit.prevent="submit">
        <label>
          反馈类型
          <select v-model="form.type" required>
            <option value="BUG">Bug</option>
            <option value="OPTIMIZATION">优化建议</option>
            <option value="REQUIREMENT">需求建议</option>
          </select>
        </label>
        <label>
          概要
          <input v-model="form.title" maxlength="120" placeholder="一句话概要（可选）" />
        </label>
        <label>
          问题描述 <span class="forgeops-required">*</span>
          <textarea v-model="form.description" rows="3" required maxlength="8000" placeholder="发生了什么？"></textarea>
        </label>
        <label>
          预期效果
          <textarea v-model="form.expectedBehavior" rows="2" maxlength="4000" placeholder="期望的正确行为"></textarea>
        </label>
        <label>
          操作步骤（可选）
          <textarea v-model="form.steps" rows="2" maxlength="4000" placeholder="复现步骤"></textarea>
        </label>
        <label>
          补充说明（可选）
          <input v-model="form.note" maxlength="4000" />
        </label>
        <label>
          你的姓名 <span class="forgeops-required">*</span>
          <input v-model="form.reporterName" required maxlength="64" placeholder="用于验证闭环通知" />
        </label>

        <details class="forgeops-auto">
          <summary>自动附带上下文（{{ autoSummary }}）</summary>
          <pre class="forgeops-pre">{{ autoContextPreview }}</pre>
        </details>

        <label v-if="ctx.options.screenshotEnabled" class="forgeops-check">
          <input v-model="form.screenshotEnabled" type="checkbox" />
          附带页面截图
        </label>

        <div v-if="error" class="forgeops-error">{{ error }}</div>
        <div v-if="success" class="forgeops-success">已提交 {{ successId }}，可在「我的反馈」中跟踪进度。</div>

        <button class="forgeops-submit" type="submit" :disabled="submitting">
          {{ submitting ? '提交中…' : '提交反馈' }}
        </button>
      </form>

      <div v-else class="forgeops-body forgeops-mine">
        <div v-if="!detail">
          <div class="forgeops-row">
            <input v-model="mineReporter" placeholder="你的姓名" maxlength="64" />
            <button type="button" @click="loadMine">查询</button>
          </div>
          <div v-if="mineError" class="forgeops-error">{{ mineError }}</div>
          <div
            v-for="item in mineList"
            :key="item.id"
            class="forgeops-item"
            @click="openDetail(item.id)"
          >
            <div class="forgeops-item-line">
              <span class="forgeops-item-id">{{ item.id }}</span>
              <span :class="['forgeops-status', statusClass(item.displayStatus)]">{{ item.displayStatus }}</span>
            </div>
            <div class="forgeops-item-title">{{ item.title }}</div>
          </div>
          <div v-if="!mineList.length && !mineError" class="forgeops-empty">暂无反馈记录</div>
        </div>

        <div v-else>
          <button class="forgeops-back" type="button" @click="detail = null">← 返回列表</button>
          <h4 class="forgeops-detail-title">{{ detail.id }} · {{ detail.title }}</h4>
          <div :class="['forgeops-status', statusClass(detail.displayStatus)]">{{ detail.displayStatus }}</div>
          <div v-if="detail.deploymentVersion" class="forgeops-meta">
            部署版本：<code>{{ detail.deploymentVersion }}</code>
          </div>
          <div v-if="detail.prUrl" class="forgeops-meta">
            PR：<a :href="detail.prUrl" target="_blank" rel="noreferrer">{{ detail.prUrl }}</a>
          </div>
          <div v-if="detail.multicaIssueUrl" class="forgeops-meta">
            Issue：<a :href="detail.multicaIssueUrl" target="_blank" rel="noreferrer">{{ detail.multicaIssueUrl }}</a>
          </div>

          <div class="forgeops-timeline">
            <div v-for="c in detail.comments" :key="c.id" class="forgeops-comment">
              <div class="forgeops-comment-head">
                <span>{{ c.author }}</span>
                <span class="forgeops-comment-time">{{ formatTime(c.createdAt) }}</span>
              </div>
              <div class="forgeops-comment-body">{{ c.content }}</div>
            </div>
          </div>

          <template v-if="detail.displayStatus === '待验证'">
            <textarea v-model="verifyNote" rows="2" placeholder="验证说明（可选）"></textarea>
            <div class="forgeops-verify">
              <button class="forgeops-pass" type="button" @click="verify(true)">验证通过</button>
              <button class="forgeops-fail" type="button" @click="verify(false)">仍有问题</button>
            </div>
          </template>
          <div v-if="verifyError" class="forgeops-error">{{ verifyError }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, onMounted, reactive, ref, watch } from 'vue'
import type { FeedbackDetail, FeedbackListItem, FeedbackSubmission } from '../types'
import { FORGEOPS_KEY, type ForgeOpsContext } from '../index'

const ctx = inject<ForgeOpsContext>(FORGEOPS_KEY)
const open = ref(false)
const tab = ref<'submit' | 'mine'>('submit')

const form = reactive({
  type: 'BUG' as FeedbackSubmission['type'],
  title: '',
  description: '',
  expectedBehavior: '',
  steps: '',
  note: '',
  reporterName: '',
  screenshotEnabled: false,
})
const submitting = ref(false)
const error = ref('')
const success = ref(false)
const successId = ref('')

const mineReporter = ref('')
const mineList = ref<FeedbackListItem[]>([])
const mineError = ref('')
const detail = ref<FeedbackDetail | null>(null)
const verifyNote = ref('')
const verifyError = ref('')

onMounted(() => {
  const reporter = ctx?.getReporter()
  if (reporter?.name) {
    form.reporterName = reporter.name
    mineReporter.value = reporter.name
  }
})

watch(
  () => form.reporterName,
  (name) => {
    if (ctx && name) ctx.lastReporter.name = name
  },
)

const failedRequests = computed(() => ctx?.collector.failedRequests() ?? [])
const autoSummary = computed(
  () => `${failedRequests.value.length} 条失败请求 · ${ctx?.collector.consoleErrorList().length ?? 0} 条控制台错误`,
)

const autoContextPreview = computed(() => {
  if (!ctx) return ''
  const payload = buildSubmission()
  // 截图字段太大，预览时省略
  const { screenshot, ...rest } = payload
  return JSON.stringify({ ...rest, screenshot: screenshot ? `<${screenshot.length} bytes>` : undefined }, null, 2)
})

function buildSubmission(): FeedbackSubmission {
  if (!ctx) throw new Error('no context')
  const opts = ctx.options
  const frontend = opts.getFrontendInfo?.() || {}
  const backend = opts.getBackendInfo?.() || {}
  return {
    schemaVersion: '1.0',
    projectId: opts.projectId,
    type: form.type,
    title: form.title || undefined,
    description: form.description,
    expectedBehavior: form.expectedBehavior || undefined,
    steps: form.steps || undefined,
    note: form.note || undefined,
    reporter: ctx.getReporter() ?? { name: form.reporterName },
    environment: opts.environment,
    page: {
      url: location.href,
      route: opts.getRouteName?.(),
      title: document.title,
    },
    client: {
      userAgent: navigator.userAgent,
      platform: navigator.platform,
      language: navigator.language,
      screen: `${screen.width}x${screen.height}`,
      viewport: `${innerWidth}x${innerHeight}`,
    },
    frontend,
    backend: backend.version || backend.commit ? backend : undefined,
    requests: failedRequests.value,
    consoleErrors: ctx.collector.consoleErrorList(),
    screenshot: undefined,
    occurredAt: new Date().toISOString(),
  }
}

async function captureScreenshot(): Promise<string | undefined> {
  if (!ctx?.options.screenshotEnabled || !form.screenshotEnabled) return undefined
  try {
    const mod = await import('html2canvas')
    const canvas = await mod.default(document.body, { logging: false, useCORS: true })
    return canvas.toDataURL('image/png')
  } catch {
    return undefined // 截图失败不阻断提交
  }
}

async function submit() {
  if (!ctx) return
  error.value = ''
  success.value = false
  submitting.value = true
  try {
    const payload = buildSubmission()
    const screenshot = await captureScreenshot()
    const result = await ctx.client.submitFeedback(screenshot ? { ...payload, screenshot } : payload)
    successId.value = result.id
    success.value = true
    form.description = ''
    form.title = ''
    form.expectedBehavior = ''
    form.steps = ''
    form.note = ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    submitting.value = false
  }
}

async function switchToMine() {
  tab.value = 'mine'
  if (mineReporter.value) await loadMine()
}

async function loadMine() {
  if (!ctx || !mineReporter.value) return
  mineError.value = ''
  try {
    mineList.value = await ctx.client.listMyFeedback(mineReporter.value, ctx.options.projectId)
  } catch (e) {
    mineError.value = e instanceof Error ? e.message : String(e)
  }
}

async function openDetail(id: string) {
  if (!ctx) return
  verifyError.value = ''
  verifyNote.value = ''
  detail.value = await ctx.client.getFeedback(id)
}

async function verify(pass: boolean) {
  if (!ctx || !detail.value) return
  verifyError.value = ''
  const verifier = ctx.getReporter()?.name || mineReporter.value || 'unknown'
  try {
    if (pass) {
      await ctx.client.verifyPass(detail.value.id, verifier, verifyNote.value || undefined)
    } else {
      if (!verifyNote.value.trim()) {
        verifyError.value = '请填写“仍有问题”的具体说明（验证说明必填）'
        return
      }
      await ctx.client.verifyFail(detail.value.id, verifier, verifyNote.value, {
        requests: ctx.collector.failedRequests(),
        consoleErrors: ctx.collector.consoleErrorList(),
      })
    }
    await openDetail(detail.value.id)
  } catch (e) {
    verifyError.value = e instanceof Error ? e.message : String(e)
  }
}

function statusClass(displayStatus: string): string {
  if (displayStatus === '已完成') return 'st-done'
  if (displayStatus === '待验证') return 'st-verify'
  if (displayStatus === '需要补充') return 'st-info'
  if (displayStatus === '待处理') return 'st-todo'
  return 'st-doing'
}

function formatTime(iso: string): string {
  return iso?.replace('T', ' ').slice(0, 19) || ''
}
</script>

<style scoped>
.forgeops-root { all: initial; }
.forgeops-root * { all: revert; box-sizing: border-box; font-family: -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif; }
.forgeops-fab {
  all: revert;
  position: fixed; right: 24px; bottom: 24px; z-index: 2147483000;
  background: #2563eb; color: #fff; border: none; border-radius: 24px;
  padding: 10px 22px; font-size: 14px; cursor: pointer; box-shadow: 0 4px 14px rgba(37, 99, 235, .4);
}
.forgeops-fab:hover { background: #1d4ed8; }
.forgeops-panel {
  position: fixed; right: 24px; bottom: 76px; z-index: 2147483000;
  width: 420px; max-width: calc(100vw - 32px); max-height: min(78vh, 720px);
  background: #fff; border-radius: 12px; box-shadow: 0 12px 40px rgba(0,0,0,.18);
  display: flex; flex-direction: column; overflow: hidden;
}
.forgeops-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid #e5e7eb; }
.forgeops-title { font-weight: 600; font-size: 15px; color: #111827; }
.forgeops-close { all: revert; background: none; border: none; cursor: pointer; font-size: 14px; color: #6b7280; }
.forgeops-tabs { display: flex; border-bottom: 1px solid #e5e7eb; }
.forgeops-tabs button { all: revert; flex: 1; padding: 10px; background: none; border: none; font-size: 13px; color: #6b7280; cursor: pointer; border-bottom: 2px solid transparent; }
.forgeops-tabs button.active { color: #2563eb; border-bottom-color: #2563eb; font-weight: 600; }
.forgeops-body { padding: 14px 16px; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; font-size: 13px; color: #111827; }
.forgeops-body label { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: #374151; }
.forgeops-body input[type="text"], .forgeops-body input:not([type]), .forgeops-body select, .forgeops-body textarea {
  border: 1px solid #d1d5db; border-radius: 6px; padding: 7px 9px; font-size: 13px; background: #fff;
}
.forgeops-body input:focus, .forgeops-body textarea:focus, .forgeops-body select:focus { outline: 2px solid #93c5fd; border-color: #2563eb; }
.forgeops-check { flex-direction: row !important; align-items: center; gap: 6px !important; }
.forgeops-required { color: #dc2626; }
.forgeops-auto summary { cursor: pointer; color: #6b7280; font-size: 12px; }
.forgeops-pre { background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 6px; padding: 8px; font-size: 11px; max-height: 220px; overflow: auto; white-space: pre-wrap; word-break: break-all; }
.forgeops-submit { all: revert; background: #2563eb; color: #fff; border: none; border-radius: 8px; padding: 10px; font-size: 14px; cursor: pointer; }
.forgeops-submit:disabled { background: #93c5fd; cursor: not-allowed; }
.forgeops-error { background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca; border-radius: 6px; padding: 8px; font-size: 12px; }
.forgeops-success { background: #f0fdf4; color: #15803d; border: 1px solid #bbf7d0; border-radius: 6px; padding: 8px; font-size: 12px; }
.forgeops-row { display: flex; gap: 8px; }
.forgeops-row input { flex: 1; border: 1px solid #d1d5db; border-radius: 6px; padding: 7px 9px; font-size: 13px; }
.forgeops-row button { all: revert; background: #f3f4f6; border: 1px solid #d1d5db; border-radius: 6px; padding: 7px 12px; cursor: pointer; font-size: 13px; }
.forgeops-item { border: 1px solid #e5e7eb; border-radius: 8px; padding: 10px; cursor: pointer; }
.forgeops-item:hover { border-color: #93c5fd; background: #f8fafc; }
.forgeops-item-line { display: flex; justify-content: space-between; margin-bottom: 4px; }
.forgeops-item-id { color: #6b7280; font-size: 12px; }
.forgeops-item-title { font-size: 13px; }
.forgeops-empty { color: #9ca3af; text-align: center; padding: 20px 0; }
.forgeops-status { font-size: 11px; padding: 2px 8px; border-radius: 10px; }
.st-todo { background: #f3f4f6; color: #6b7280; }
.st-doing { background: #eff6ff; color: #2563eb; }
.st-verify { background: #fef3c7; color: #b45309; }
.st-done { background: #f0fdf4; color: #15803d; }
.st-info { background: #fdf2f8; color: #be185d; }
.forgeops-back { all: revert; background: none; border: none; color: #2563eb; cursor: pointer; font-size: 13px; padding: 0; align-self: flex-start; }
.forgeops-detail-title { all: revert; margin: 4px 0; font-size: 14px; color: #111827; }
.forgeops-meta { font-size: 12px; color: #374151; word-break: break-all; }
.forgeops-meta a { color: #2563eb; }
.forgeops-timeline { display: flex; flex-direction: column; gap: 8px; max-height: 260px; overflow-y: auto; }
.forgeops-comment { border: 1px solid #e5e7eb; border-radius: 8px; padding: 8px 10px; }
.forgeops-comment-head { display: flex; justify-content: space-between; font-size: 11px; color: #6b7280; margin-bottom: 4px; }
.forgeops-comment-body { font-size: 12px; white-space: pre-wrap; word-break: break-word; color: #111827; }
.forgeops-verify { display: flex; gap: 8px; }
.forgeops-pass { all: revert; flex: 1; background: #16a34a; color: #fff; border: none; border-radius: 8px; padding: 9px; cursor: pointer; font-size: 13px; }
.forgeops-fail { all: revert; flex: 1; background: #dc2626; color: #fff; border: none; border-radius: 8px; padding: 9px; cursor: pointer; font-size: 13px; }
</style>
