/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import fs from 'node:fs'
import path from 'node:path'
import {fileURLToPath, URL} from 'node:url'
import {defineConfig} from 'vite'
import vueDevTools from 'vite-plugin-vue-devtools'
import {VitePWA} from 'vite-plugin-pwa'
import {sentryVitePlugin} from '@sentry/vite-plugin'

export default defineConfig(({ command }) => {
  const isDev = command === 'serve' && !process.env.VITEST

  const certsDir = path.resolve(__dirname, '../developer-local-settings/config/certs')
  // Hostname Vite serves under. Override via VITE_DEV_HOST (e.g. CI uses
  // 'e2e-chainlink.localhost' to keep e2e cookies/storage isolated). The
  // matching cert files are written by scripts/certs/generate-keypair.sh.
  const devHost = process.env.VITE_DEV_HOST ?? 'local-chainlink.localhost'
  const certFile = path.join(certsDir, `${devHost}.pem`)
  const keyFile = path.join(certsDir, `${devHost}.key`)

  return {
    plugins: [
      vue(),
      vueDevTools(),
      tailwindcss(),
      sentryVitePlugin({
        org: 'mh03r932',
        project: 'chainlink-vue',
        authToken: process.env.SENTRY_AUTH_TOKEN,
        // Only upload in CI — skip silently when token is absent
        disable: !process.env.SENTRY_AUTH_TOKEN,
      }),
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
      allowedHosts: ['dev-chainlink.markushofstetter.com', 'local-chainlink.localhost', devHost],
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
          target: process.env.VITE_API_TARGET ?? 'https://dev-chainlink.markushofstetter.com:8443',
          changeOrigin: true,
          secure: false,
          configure: (proxy, _options) => {
            proxy.on('proxyReq', (proxyReq, _req, _res) => {
              proxyReq.setHeader('X-Forwarded-Host', 'dev-chainlink.markushofstetter.com')
              proxyReq.setHeader('X-Forwarded-Proto', 'https')
              proxyReq.setHeader('X-Forwarded-Port', '5173')
            })
            // Mirror Caddy's behavior: upstream-down → 502, timeout → 504.
            // Without this handler, http-proxy emits a socket hang up that
            // surfaces as a TypeError in the browser, diverging from prod.
            proxy.on('error', (err: NodeJS.ErrnoException, _req, res) => {
              if (!('writeHead' in res) || res.headersSent) return
              const status = err.code === 'ETIMEDOUT' || err.code === 'ECONNRESET' ? 504 : 502
              res.writeHead(status, { 'Content-Type': 'text/plain' })
              res.end(`${status === 504 ? 'Gateway Timeout' : 'Bad Gateway'}: ${err.code ?? err.message}`)
            })
          },
        },
      },
    },
    build: {
      sourcemap: 'hidden',
      rollupOptions: {
        output: {
          manualChunks: {
            vue: ['vue', 'vue-router', 'pinia', 'vue-i18n'],
            validation: ['vee-validate', '@vee-validate/zod', 'zod'],
            ui: ['radix-vue', '@lucide/vue'],
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
