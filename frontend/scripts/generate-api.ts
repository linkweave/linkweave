import { exec } from 'node:child_process'
import { existsSync, rmSync, writeFileSync } from 'node:fs'
import { request } from 'node:https'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const OPENAPI_URL = 'https://local-linkweave.localhost:8443/q/openapi'
const OUTPUT_DIR = 'src/api/generated'

function fetchSpec(url: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const req = request(url, { rejectUnauthorized: false }, (res) => {
      if (res.statusCode !== 200) {
        reject(new Error(`Failed to fetch ${url}: HTTP ${res.statusCode}`))
        return
      }
      const chunks: Buffer[] = []
      res.on('data', (chunk: Buffer) => chunks.push(chunk))
      res.on('end', () => resolve(Buffer.concat(chunks).toString('utf-8')))
      res.on('error', reject)
    })
    req.on('error', reject)
    req.end()
  })
}

async function main() {
  console.log('Fetching OpenAPI spec from', OPENAPI_URL)
  const spec = await fetchSpec(OPENAPI_URL)
  const specFile = join(tmpdir(), `linkweave-openapi-${process.pid}.json`)
  writeFileSync(specFile, spec)

  if (existsSync(OUTPUT_DIR)) {
    rmSync(OUTPUT_DIR, { recursive: true })
    console.log('Cleaned', OUTPUT_DIR)
  }
  const cmd = [
    'npx @openapitools/openapi-generator-cli generate',
    `  -i ${specFile}`,
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
      code === 0 ? resolve() : reject(new Error('Generator exited with code ' + code)),
    )
    child.on('error', reject)
  })

  rmSync(specFile, { force: true })
  console.log('Done. Generated to', OUTPUT_DIR)
}


void main().catch((err: unknown) => {
  console.error(err)
  process.exit(1)
})
