import { existsSync, mkdirSync, readFileSync, renameSync, rmSync, unlinkSync, writeFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { dirname, join } from 'node:path'

import { CliError } from './errors'

export const DEFAULT_SERVER = 'https://linkweave.dev'

/** Raw API keys are `lw_` followed by 64 hex chars (UC-080 step 6). */
export const API_KEY_PATTERN = /^lw_[0-9a-f]{64}$/

export interface StoredConfig {
  server: string
  apiKey: string
  userEmail?: string
  defaultCollectionId?: string
}

export function configDir(): string {
  return join(homedir(), '.linkweave')
}

export function configPath(): string {
  return join(configDir(), 'config.json')
}

export function loadStoredConfig(path: string = configPath()): StoredConfig | undefined {
  if (!existsSync(path)) return undefined
  let raw: string
  try {
    raw = readFileSync(path, 'utf-8')
  } catch {
    throw new CliError(`Cannot read ${path}. Check file permissions.`)
  }
  try {
    return JSON.parse(raw) as StoredConfig
  } catch {
    // A corrupt file must not brick the CLI: treat it as "not logged in" so
    // `linkweave login` can recreate it (a throw here would block login too).
    process.stderr.write(
      `Warning: ignoring ${path} — not valid JSON. Run 'linkweave login' to recreate it.\n`,
    )
    return undefined
  }
}

/**
 * Writes the config with owner-only permissions (BR-021/BR-022). The content
 * goes to a fresh same-directory temp file (0600 is honored at creation) and
 * is renamed over the target: the key is never on disk with looser
 * permissions — writeFileSync's `mode` is ignored for existing files, which
 * would briefly expose the new content under the umask — and the replacement
 * is atomic.
 */
export function saveStoredConfig(config: StoredConfig, path: string = configPath()): void {
  const dir = dirname(path)
  const tmp = join(dir, `.config.json.${process.pid}.tmp`)
  try {
    mkdirSync(dir, { recursive: true, mode: 0o700 })
    writeFileSync(tmp, JSON.stringify(config, null, 2) + '\n', { mode: 0o600 })
    renameSync(tmp, path)
  } catch (e) {
    rmSync(tmp, { force: true })
    if (e instanceof CliError) throw e
    throw new CliError(`Cannot write to ${path}. Check directory permissions.`)
  }
}

/** Deletes the config file (BR-025). Returns false when there was none. */
export function deleteStoredConfig(path: string = configPath()): boolean {
  if (!existsSync(path)) return false
  unlinkSync(path)
  return true
}

export interface EffectiveConfig {
  server: string
  apiKey?: string
  userEmail?: string
  defaultCollectionId?: string
}

export interface ConfigFlags {
  server?: string
  apiKey?: string
}

/** Strips trailing slashes so `${server}/api/...` URLs stay well-formed. */
export function normalizeServer(url: string): string {
  return url.replace(/\/+$/, '')
}

/**
 * Merges CLI flags, environment variables, and the stored config with the
 * precedence flags > env > file (BR-023). The stored default collection and
 * email only apply when the effective key IS the stored key — a key injected
 * via flag/env may belong to a different user or server.
 */
export function resolveEffectiveConfig(
  flags: ConfigFlags,
  env: NodeJS.ProcessEnv = process.env,
  stored: StoredConfig | undefined = loadStoredConfig(),
): EffectiveConfig {
  const apiKey = flags.apiKey ?? env['LINKWEAVE_API_KEY'] ?? stored?.apiKey
  const server = normalizeServer(
    flags.server ?? env['LINKWEAVE_SERVER'] ?? stored?.server ?? DEFAULT_SERVER,
  )
  const usingStoredIdentity =
    stored !== undefined && apiKey === stored.apiKey && server === normalizeServer(stored.server)
  return {
    server,
    apiKey,
    userEmail: usingStoredIdentity ? stored.userEmail : undefined,
    defaultCollectionId: usingStoredIdentity ? stored.defaultCollectionId : undefined,
  }
}
