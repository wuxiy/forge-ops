import React from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'

// ===== ForgeOps 接入（React 项目两行）=====
// 1. 引入依赖 @forgeops/feedback-react
// 2. 应用挂载后初始化：右下角自动出现「反馈」入口
import { initForgeOpsFeedback } from '@forgeops/feedback-react'

const backendInfo: { version?: string; commit?: string } = {}
fetch('/api/version')
  .then((r) => r.json())
  .then((v) => {
    backendInfo.version = v.version
    backendInfo.commit = v.commit
  })
  .catch(() => {})

initForgeOpsFeedback({
  gatewayUrl: (import.meta.env.VITE_FORGEOPS_GATEWAY as string) || 'http://localhost:8080',
  projectId: 'demo-app',
  environment: 'test',
  getReporter() {
    const name = localStorage.getItem('demo-user')
    return name ? { id: name, name } : null
  },
  getFrontendInfo: () => ({ version: '0.1.0', commit: 'react-demo' }),
  getBackendInfo: () => backendInfo,
})

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
