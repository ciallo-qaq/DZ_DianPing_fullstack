import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  build: {
    // 每个前端服务构建到 Nginx html 下自己的目录，避免互相覆盖。
    outDir: 'F:/nginx-1.30.6/html/DZ_DianPing_Front',
    emptyOutDir: true
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})
