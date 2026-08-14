import { createApp } from 'vue'
import { createForgeOps } from '@forgeops/feedback-vue'
import App from './App.vue'
import './style.css'

/** 启动时拉取后端版本（SDK 提交反馈时自动附带）。 */
const backendInfo: { version?: string; commit?: string } = {}
fetch('/api/version')
  .then((r) => r.json())
  .then((v) => {
    backendInfo.version = v.version
    backendInfo.commit = v.commit
  })
  .catch(() => {
    /* 版本获取失败不阻断应用 */
  })

const forgeops = createForgeOps({
  gatewayUrl: (import.meta.env.VITE_FORGEOPS_GATEWAY as string) || 'http://localhost:8080',
  projectId: 'demo-app',
  environment: 'test',
  getReporter() {
    const name = localStorage.getItem('demo-user')
    return name ? { id: name, name } : null
  },
  getFrontendInfo: () => ({ version: __APP_VERSION__, commit: __APP_COMMIT__ }),
  getBackendInfo: () => backendInfo,
  screenshotEnabled: false,
})

createApp(App).use(forgeops.plugin).mount('#app')
