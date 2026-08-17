import Vue from 'vue'
import App from './App.vue'

// ===== ForgeOps 接入（Vue2 项目两行）=====
import { createForgeOps } from '@forgeops/feedback-vue2'

Vue.use(
  createForgeOps({
    gatewayUrl: 'http://localhost:8080',
    projectId: 'demo-app',
    environment: 'test',
    getReporter() {
      const name = localStorage.getItem('demo-user')
      return name ? { id: name, name } : null
    },
    getFrontendInfo: () => ({ version: '0.1.0', commit: 'vue2-demo' }),
    getBackendInfo() {
      return window.__backendInfo__ || null
    },
  }),
)

fetch('/api/version')
  .then((r) => r.json())
  .then((v) => {
    window.__backendInfo__ = { version: v.version, commit: v.commit }
  })
  .catch(() => {})

new Vue({ render: (h) => h(App) }).$mount('#app')
