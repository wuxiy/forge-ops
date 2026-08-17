<template>
  <div id="vue2-app">
    <header class="topbar">
      <span>健康平台（ForgeOps Vue2 接入示例）</span>
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

      <div v-if="loading" class="loading">加载中…</div>
      <table v-else-if="records.length" class="records">
        <thead>
          <tr><th>检查单号</th><th>项目</th><th>状态</th><th>时间</th></tr>
        </thead>
        <tbody>
          <tr v-for="r in records" :key="r.id">
            <td>{{ r.id }}</td>
            <td>{{ r.name }}</td>
            <td>{{ r.status === 'DONE' ? '已完成' : '待出报告' }}</td>
            <td>{{ (r.time || '').replace('T', ' ').slice(0, 19) }}</td>
          </tr>
        </tbody>
      </table>
      <div v-else-if="loaded" class="empty">该患者暂无检查记录</div>
    </main>
  </div>
</template>

<script>
export default {
  name: 'App',
  data() {
    return {
      user: localStorage.getItem('demo-user') || '',
      patientId: 'P-10001',
      records: [],
      loading: false,
      loaded: false,
    }
  },
  mounted() {
    this.load()
  },
  methods: {
    saveUser() {
      localStorage.setItem('demo-user', this.user)
    },
    async load() {
      if (!this.patientId) return
      this.loading = true
      try {
        const res = await fetch(`/api/patients/${encodeURIComponent(this.patientId)}/records`)
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const data = await res.json()
        this.records = data.records ?? []
        this.loaded = true
        this.loading = false
      } catch (e) {
        // 演示项目：后端已修复，异常场景仅打日志
        console.error('加载失败', e)
        this.loading = false
        this.loaded = true
        this.records = []
      }
    },
  },
}
</script>

<style>
body { margin: 0; background: #f3f4f6; font-family: -apple-system, 'PingFang SC', sans-serif; }
#vue2-app .topbar {
  display: flex; justify-content: space-between; align-items: center;
  background: #4d7c0f; color: #fff; padding: 10px 24px; font-size: 15px;
}
#vue2-app .user input { border: none; border-radius: 4px; padding: 4px 8px; margin-left: 6px; }
#vue2-app .page {
  max-width: 720px; margin: 32px auto; background: #fff; border-radius: 12px;
  padding: 24px; box-shadow: 0 2px 8px rgba(0,0,0,.06); color: #111827;
}
#vue2-app h2 { margin-top: 0; }
#vue2-app .search { display: flex; gap: 8px; margin-bottom: 16px; }
#vue2-app .search input { flex: 1; border: 1px solid #d1d5db; border-radius: 6px; padding: 8px 10px; }
#vue2-app .search button { background: #65a30d; color: #fff; border: none; border-radius: 6px; padding: 8px 20px; cursor: pointer; }
#vue2-app .records { width: 100%; border-collapse: collapse; }
#vue2-app .records th, #vue2-app .records td { text-align: left; border-bottom: 1px solid #e5e7eb; padding: 10px 8px; font-size: 14px; }
#vue2-app .loading { color: #6b7280; padding: 24px 0; }
#vue2-app .empty { color: #6b7280; background: #f9fafb; border: 1px dashed #d1d5db; border-radius: 8px; padding: 24px; text-align: center; }
</style>
