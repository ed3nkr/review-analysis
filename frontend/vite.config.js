import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// /api 로 시작하는 요청은 Spring(8080)으로 넘긴다 → CORS 설정 없이 개발 가능
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
