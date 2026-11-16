import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  const proxy = {
    '/api': {
      target: env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
      changeOrigin: true,
    },
    '/models': {
      target: env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
      changeOrigin: true,
    },
  }

  return {
    plugins: [vue()],
    server: {
      port: 5173,
      proxy,
    },
    build: {
      outDir: 'dist',
      emptyOutDir: true,
    },
  }
})
