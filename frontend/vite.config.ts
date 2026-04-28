/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import fs from 'node:fs'
import path from 'node:path'
import {fileURLToPath, URL} from 'node:url'
import {defineConfig} from 'vite'
import vueDevTools from 'vite-plugin-vue-devtools'
import {VitePWA} from 'vite-plugin-pwa'

export default defineConfig(({ command }) => {
  const isDev = command === 'serve'

  const certsDir = path.resolve(__dirname, '../developer-local-settings/config/certs')
  const certFile = path.join(certsDir, 'local-chainlink.localhost.pem')
  const keyFile = path.join(certsDir, 'local-chainlink.localhost.key')

  return {
    plugins: [
      vue(),
      vueDevTools(),
      tailwindcss(),
      VitePWA({
        registerType: 'autoUpdate',
        workbox: {
          navigateFallback: 'index.html',
          navigateFallbackDenylist: [/^\/api/],
          runtimeCaching: [
            {
              urlPattern: /^https:\/\/.*\.(js|css|png|svg|ico|woff2)$/,
              handler: 'CacheFirst',
              options: { cacheName: 'chainlink-assets' },
            },
          ],
        },
        manifest: {
          name: 'Chainlink - Bookmark Manager',
          short_name: 'Chainlink',
          description: 'Self-hosted bookmark manager',
          theme_color: '#2563eb',
          background_color: '#ffffff',
          display: 'standalone',
          icons: [
            { src: '/chainlink-favicon.png', sizes: '192x192', type: 'image/png' },
            { src: '/chainlink-favicon.png', sizes: '512x512', type: 'image/png' },
          ],
        },
      }),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 5173,
      host: true,
      allowedHosts: ['dev-chainlink.markushofstetter.com', 'local-chainlink.localhost'],
      ...(isDev
        ? {
            https: {
              cert: fs.readFileSync(certFile),
              key: fs.readFileSync(keyFile),
            },
          }
        : {}),
      proxy: {
        '/api': {
          target: 'https://dev-chainlink.markushofstetter.com:8443',
          changeOrigin: true,
          secure: false,
          configure: (proxy, _options) => {
            proxy.on('proxyReq', (proxyReq, _req, _res) => {
              proxyReq.setHeader('X-Forwarded-Host', 'dev-chainlink.markushofstetter.com')
              proxyReq.setHeader('X-Forwarded-Proto', 'https')
              proxyReq.setHeader('X-Forwarded-Port', '5173')
            })
          },
        },
      },
    },
    test: {
      globals: true,
      environment: 'node',
      exclude: ['node_modules', 'dist', 'e2e/**'],
    },
  }
})
