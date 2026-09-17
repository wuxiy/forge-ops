import { createApp } from 'vue'
import { initForgeOpsFeedback } from '@forgeops/feedback-dom'
import App from './App.vue'
import './style.css'

const app = createApp(App)
app.mount('#app')

initForgeOpsFeedback({
  gatewayUrl: (import.meta.env.VITE_FORGEOPS_GATEWAY as string) || 'http://localhost:8080',
  projectId: 'demo-app',
  async getToken() {
    const response = await fetch('/api/forgeops/token')
    if (!response.ok) throw new Error('Host did not issue a ForgeOps token')
    return response.text()
  },
})
