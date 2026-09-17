import React from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'

import { initForgeOpsFeedback } from '@forgeops/feedback-dom'

initForgeOpsFeedback({
  gatewayUrl: (import.meta.env.VITE_FORGEOPS_GATEWAY as string) || 'http://localhost:8080',
  projectId: 'demo-app',
  async getToken() {
    const response = await fetch('/api/forgeops/token')
    if (!response.ok) throw new Error('Host did not issue a ForgeOps token')
    return response.text()
  },
})

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
