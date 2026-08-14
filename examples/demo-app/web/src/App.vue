<template>
  <header class="topbar">
    <span>健康平台（ForgeOps 试点 Demo）</span>
    <span class="user">
      当前用户：
      <input v-model="user" placeholder="你的姓名" @change="saveUser" />
    </span>
  </header>

  <main class="page">
    <h2>检查记录</h2>

    <div class="search">
      <input v-model="patientId" placeholder="患者ID，如 P-10001" @keyup.enter="load" />
      <button type="button" @click="load">查询</button>
    </div>

    <div v-if="loading" class="loading">
      <span class="spinner"></span>
      加载中…
    </div>

    <table v-else-if="records.length" class="records">
      <thead>
        <tr>
          <th>检查单号</th>
          <th>项目</th>
          <th>状态</th>
          <th>时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in records" :key="r.id">
          <td>{{ r.id }}</td>
          <td>{{ r.name }}</td>
          <td>{{ r.status === 'DONE' ? '已完成' : '待出报告' }}</td>
          <td>{{ r.time?.replace('T', ' ').slice(0, 19) }}</td>
        </tr>
      </tbody>
    </table>

    <div v-else-if="loaded" class="empty">该患者暂无检查记录</div>

    <div v-else-if="error" class="empty error">{{ error }}</div>
  </main>

  <ForgeOpsWidget />
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'

interface ExamRecord {
  id: string
  name: string
  status: string
  time: string
}

const user = ref(localStorage.getItem('demo-user') || '')
const patientId = ref('P-10001')
const records = ref<ExamRecord[]>([])
const loading = ref(false)
const loaded = ref(false)
const error = ref('')

// 弱网优化：单次查询最长等待 8 秒，超时中止并提示；新查询会取消上一个在途请求。
const REQUEST_TIMEOUT_MS = 8000
let requestSeq = 0
let abortController: AbortController | null = null

function saveUser() {
  localStorage.setItem('demo-user', user.value)
}

async function load() {
  if (!patientId.value) return
  abortController?.abort()
  const controller = new AbortController()
  abortController = controller
  const seq = ++requestSeq

  loading.value = true
  error.value = ''
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)
  try {
    const res = await fetch(`/api/patients/${encodeURIComponent(patientId.value)}/records`, {
      signal: controller.signal,
    })
    if (!res.ok) {
      throw new Error(`查询失败（${res.status}），请稍后重试`)
    }
    const data = await res.json()
    if (seq !== requestSeq) return // 已被更新的查询取代，丢弃过期响应
    records.value = data.records ?? []
    loaded.value = true
  } catch (e) {
    if (seq !== requestSeq) return // 已被更新的查询取代，loading 由新请求负责
    // 请求失败、超时或响应非 JSON 时复位 loading 并展示提示，避免页面停留在「加载中…」。
    records.value = []
    loaded.value = false
    if (e instanceof DOMException && e.name === 'AbortError') {
      error.value = `查询超时（${REQUEST_TIMEOUT_MS / 1000} 秒），请检查网络后重试`
    } else {
      error.value = e instanceof Error ? e.message : '查询失败，请稍后重试'
    }
  } finally {
    clearTimeout(timer)
    if (seq === requestSeq) {
      loading.value = false
    }
  }
}

onMounted(load)
</script>

<style scoped>
.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #1e40af;
  color: #fff;
  padding: 10px 24px;
  font-size: 15px;
}
.user input {
  border: none;
  border-radius: 4px;
  padding: 4px 8px;
  margin-left: 6px;
}
.page {
  max-width: 720px;
  margin: 32px auto;
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.search {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.search input {
  flex: 1;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 8px 10px;
}
.search button {
  background: #2563eb;
  color: #fff;
  border: none;
  border-radius: 6px;
  padding: 8px 20px;
  cursor: pointer;
}
.records {
  width: 100%;
  border-collapse: collapse;
}
.records th,
.records td {
  text-align: left;
  border-bottom: 1px solid #e5e7eb;
  padding: 10px 8px;
  font-size: 14px;
}
.loading {
  color: #6b7280;
  padding: 24px 0;
  display: flex;
  align-items: center;
  gap: 10px;
}
.spinner {
  width: 18px;
  height: 18px;
  border: 3px solid #dbeafe;
  border-top-color: #2563eb;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.empty {
  color: #6b7280;
  background: #f9fafb;
  border: 1px dashed #d1d5db;
  border-radius: 8px;
  padding: 24px;
  text-align: center;
}
.empty.error {
  color: #b91c1c;
  background: #fef2f2;
  border-color: #fecaca;
}
</style>
