import { exec } from 'node:child_process'
import { existsSync, rmSync } from 'node:fs'

const OPENAPI_URL = 'https://local-chainlink.localhost:8443/q/openapi'
const OUTPUT_DIR = 'src/api/generated'

async function main() {
  if (existsSync(OUTPUT_DIR)) {
    rmSync(OUTPUT_DIR, { recursive: true })
    console.log('Cleaned', OUTPUT_DIR)
  }
  const cmd = [
    'npx @openapitools/openapi-generator-cli generate',
    `  -i ${OPENAPI_URL}`,
    '  -g typescript-fetch',
    `  -o ${OUTPUT_DIR}`,
    '  --global-property models,apis,supportingFiles',
    '  -p supportsES6=true',
    '  -p prefixParameterInterfaces=true',
    '  -p fileNaming=kebab-case',
    '  -p stringEnums=false',
    '  --type-mappings entity-id=string,email=string,semantic-version=string,url=string',
    '  --openapi-normalizer KEEP_ONLY_FIRST_TAG_IN_OPERATION=true',
  ].join(' \\\n')

  console.log('Running generator...')
  const child = exec(cmd)
  child.stdout?.on('data', (data: string) => console.log(data.toString()))
  child.stderr?.on('data', (data: string) => console.error(data.toString()))

  await new Promise<void>((resolve, reject) => {
    child.on('exit', (code: number | null) =>
      code === 0
        ? resolve()
        : reject(new Error('Generator exited with code ' + code)),
    )
    child.on('error', reject)
  })

  console.log('Done. Generated to', OUTPUT_DIR)
}

main().catch((err: unknown) => {
  console.error(err)
  process.exit(1)
})
