import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import fs from 'node:fs'
import path from 'node:path'
import {fileURLToPath, URL} from 'node:url'
import {defineConfig} from 'vite'
import vueDevTools from 'vite-plugin-vue-devtools'

export default defineConfig(({ command }) => {
  const isDev = command === 'serve'

  const certsDir = path.resolve(__dirname, '../developer-local-settings/config/certs')
  const certFile = path.join(certsDir, 'local-chainlink.localhost.pem')
  const keyFile = path.join(certsDir, 'local-chainlink.localhost.key')

  return {
    plugins: [vue(), vueDevTools(), tailwindcss()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      port: 5173,
      ...(isDev ? {
        https: {
          cert: fs.readFileSync(certFile),
          key: fs.readFileSync(keyFile)
        }
      } : {}),
      proxy: {
        '/api': {
          target: 'https://local-chainlink.localhost:8443',
          changeOrigin: true,
          secure: false
        }
      }
    }
  }
})
