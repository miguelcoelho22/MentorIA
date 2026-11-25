import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  base: '/teacher/',
  server: {
    proxy: {
      '/api': process.env.GATEWAY_URL ?? 'http://localhost:8080',
    },
  },
})
