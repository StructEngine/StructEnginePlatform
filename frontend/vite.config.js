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

  // 开发期把 /squaremap 反代到 Squaremap Web Server，规避跨域与 CORS 配置。
  // 生产环境请由 Nginx / Caddy 提供同名反向代理，或直接把 VITE_SQUAREMAP_URL 配成完整 URL。
  if (env.VITE_SQUAREMAP_PROXY_TARGET) {
    proxy['/squaremap'] = {
      target: env.VITE_SQUAREMAP_PROXY_TARGET,
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/squaremap/, ''),
    }
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
