import { defineConfig } from 'tsup'

export default defineConfig({
  entry: { main: 'src/main.ts' },
  format: 'esm',
  platform: 'node',
  target: 'node24',
  clean: true,
  // The bin entry must be directly executable via the npm shim.
  banner: { js: '#!/usr/bin/env node' },
})
