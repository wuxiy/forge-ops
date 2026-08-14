import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    lib: {
      entry: 'src/index.ts',
      name: 'ForgeOpsFeedback',
      formats: ['es'],
      fileName: 'forgeops-feedback-vue',
    },
    rollupOptions: {
      external: ['vue', 'html2canvas'],
    },
  },
})
