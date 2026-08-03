import { defineConfig } from 'vitest/config'

export default defineConfig({
  oxc: {
    // @ts-expect-error -- `tsconfig` is missing from vite's OxcOptions type but
    // is forwarded to the oxc transform. Inline options stop the per-file
    // tsconfig discovery: the imported generated client (frontend/src/api/
    // generated) would otherwise resolve frontend/tsconfig.json, whose extends
    // chain needs frontend/node_modules — not installed in the CLI CI job.
    tsconfig: {
      compilerOptions: { target: 'ES2023', useDefineForClassFields: true },
    },
  },
})
