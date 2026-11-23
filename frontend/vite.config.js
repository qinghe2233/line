import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发期把 /api 代理到本地后端，前端代码里不需要写死 8080 端口，也不会有跨域问题
export default defineConfig({
  plugins: [vue()],
  server: {
    // 固定 IPv4：默认只监听 ::1，浏览器用 127.0.0.1 访问会连接失败
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
})
