import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import { defineConfig } from 'vite'

export default defineConfig({
  // Root is the extension source dir so popup.html resolves to dist-extension/popup.html
  root: resolve(__dirname, 'src/extension'),
  // Load .env.extension.* files from the frontend directory
  envDir: resolve(__dirname),
  // Static assets (manifest.json, icons) copied into dist-extension/
  publicDir: resolve(__dirname, 'extension-public'),
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      // Keep @/ pointing to src/ so all existing imports work unchanged
      '@': resolve(__dirname, 'src'),
    },
  },
  build: {
    outDir: resolve(__dirname, 'dist-extension'),
    emptyOutDir: true,
    rollupOptions: {
      input: {
        popup: resolve(__dirname, 'src/extension/popup.html'),
        options: resolve(__dirname, 'src/extension/options.html'),
        'service-worker': resolve(__dirname, 'src/extension/background/service-worker.ts'),
      },
      output: {
        entryFileNames: '[name].js',
        chunkFileNames: 'chunks/[name]-[hash].js',
        assetFileNames: 'assets/[name][extname]',
      },
    },
  },
})
