import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import fs from 'node:fs'
import path from 'node:path'
import {fileURLToPath, URL} from 'node:url'
import {defineConfig} from 'vite'
import vueDevTools from 'vite-plugin-vue-devtools'

const certsDir = path.resolve(__dirname, '../developer-local-settings/config/certs')




export default defineConfig({
  plugins: [vue(), vueDevTools(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    https: {
      cert: fs.readFileSync(path.join(certsDir, 'local-chainlink.localhost.pem')),
      key: fs.readFileSync(path.join(certsDir, 'local-chainlink.localhost.key'))
    },
    proxy: {
      '/api': {
        target: 'https://local-chainlink.localhost:8443',
        changeOrigin: true,
        secure: false
      }
    }
  }
})
