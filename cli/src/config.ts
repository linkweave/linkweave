import {
  existsSync,
  mkdirSync,
  readFileSync,
  renameSync,
  rmSync,
  unlinkSync,
  writeFileSync,
} from 'node:fs'
import { randomBytes } from 'node:crypto'
import { homedir } from 'node:os'
import { basename, dirname, join } from 'node:path'

import { CliError, EXIT_USAGE } from './errors'

export const DEFAULT_SERVER = 'https://linkweave.dev'

/** Raw API keys are `lw_` followed by 64 hex chars (UC-080 step 6). */
export const API_KEY_PATTERN = /^lw_[0-9a-f]{64}$/

export interface StoredConfig {
  server: string
  apiKey: string
  userEmail?: string
  defaultCollectionId?: string
}

function homeFrom(env: NodeJS.ProcessEnv): string {
  // Mirrors what os.homedir() does, but off the env that was passed in, so
  // every path in this module derives from one source.
  return env['HOME'] ?? env['USERPROFILE'] ?? homedir()
}

/**
 * XDG Base Directory resolution. `XDG_CONFIG_HOME`/`XDG_CACHE_HOME` win when
 * set to an absolute path — the spec requires relative values to be ignored —
 * otherwise the platform default applies.
 *
 * The defaults are the XDG ones on macOS too. Apple's own convention is
 * `~/Library/Application Support`, but that is a poor fit for a tool driven
 * from a terminal: awkward to `cat`, `grep`, or keep under version control,
 * and unlike every other CLI already on a developer's PATH.
 */
function xdgDir(env: NodeJS.ProcessEnv, variable: string, fallback: string): string {
  const configured = env[variable]
  if (configured !== undefined && configured.startsWith('/')) return join(configured, 'linkweave')
  if (process.platform === 'win32') {
    const appData = env[variable === 'XDG_CACHE_HOME' ? 'LOCALAPPDATA' : 'APPDATA']
    if (appData) return join(appData, 'linkweave')
  }
  return join(homeFrom(env), fallback, 'linkweave')
}

export function configDir(env: NodeJS.ProcessEnv = process.env): string {
  return xdgDir(env, 'XDG_CONFIG_HOME', '.config')
}

export function cacheDir(env: NodeJS.ProcessEnv = process.env): string {
  return xdgDir(env, 'XDG_CACHE_HOME', '.cache')
}

export function configPath(env: NodeJS.ProcessEnv = process.env): string {
  return join(configDir(env), 'config.json')
}

function optionalString(value: unknown): boolean {
  return value === undefined || typeof value === 'string'
}

const SERVER_PROTOCOLS = new Set(['http:', 'https:'])

/** Parses a server URL, returning undefined when it is not usable as a base. */
function parseServerUrl(value: string): URL | undefined {
  let parsed: URL
  try {
    parsed = new URL(value.trim())
  } catch {
    return undefined
  }
  return SERVER_PROTOCOLS.has(parsed.protocol) ? parsed : undefined
}

function isStoredConfig(value: unknown): value is StoredConfig {
  if (typeof value !== 'object' || value === null) return false
  const record = value as Record<string, unknown>
  const server = record['server']
  return (
    // A stored server that no longer parses is treated as a malformed field so
    // the file is ignored — normalizeServer would otherwise throw and take
    // `linkweave login`, the only way to fix it, down with it.
    typeof server === 'string' &&
    parseServerUrl(server) !== undefined &&
    typeof record['apiKey'] === 'string' &&
    optionalString(record['userEmail']) &&
    optionalString(record['defaultCollectionId'])
  )
}

/** Writes the warning emitted when a config file has to be ignored. */
export type ConfigWarning = (message: string) => void

const warnOnStderr: ConfigWarning = (message) => {
  process.stderr.write(message)
}

export function loadStoredConfig(
  path: string = configPath(),
  // Shell completion passes a no-op: a warning printed mid-completion would
  // land in the middle of the user's command line.
  warn: ConfigWarning = warnOnStderr,
): StoredConfig | undefined {
  if (!existsSync(path)) return undefined
  let raw: string
  try {
    raw = readFileSync(path, 'utf-8')
  } catch {
    throw new CliError(`Cannot read ${path}. Check file permissions.`)
  }
  // A corrupt file must not brick the CLI: treat it as "not logged in" so
  // `linkweave login` can recreate it (a throw here would block login too).
  const ignore = (reason: string): undefined => {
    warn(`Warning: ignoring ${path} — ${reason}. Run 'linkweave login' to recreate it.\n`)
    return undefined
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    return ignore('not valid JSON')
  }
  return isStoredConfig(parsed) ? parsed : ignore('missing or malformed fields')
}

/**
 * Writes owner-only content (BR-021/BR-022) into the config dir. The content
 * goes to a fresh same-directory temp file and is renamed over the target, so
 * it is never on disk with looser permissions — writeFileSync's `mode` is
 * ignored for a file that already exists, which would briefly expose it under
 * the umask — and the replacement is atomic.
 *
 * The temp name is random and the write is exclusive (`wx` = O_CREAT|O_EXCL).
 * A predictable name plus a plain write is a handhold whenever the directory
 * is writable by anyone else: pre-create that path, and the API key is written
 * into a file — or through a symlink — of their choosing, with `mode` silently
 * ignored because the file already exists. O_EXCL refuses both.
 */
export function writePrivateFile(path: string, content: string): void {
  const dir = dirname(path)
  const tmp = join(dir, `.${basename(path)}.${randomBytes(8).toString('hex')}.tmp`)
  try {
    mkdirSync(dir, { recursive: true, mode: 0o700 })
    writeFileSync(tmp, content, { mode: 0o600, flag: 'wx' })
    renameSync(tmp, path)
  } catch (e) {
    rmSync(tmp, { force: true })
    if (e instanceof CliError) throw e
    throw new CliError(`Cannot write to ${path}. Check directory permissions.`)
  }
}

export function saveStoredConfig(config: StoredConfig, path: string = configPath()): void {
  writePrivateFile(path, JSON.stringify(config, null, 2) + '\n')
}

/**
 * Deletes the config file (BR-025). Returns false when there was none —
 * decided from unlink's own result rather than a preceding existsSync, which
 * both races and lets an unreadable-directory failure escape as a raw errno.
 */
export function deleteStoredConfig(path: string = configPath()): boolean {
  try {
    unlinkSync(path)
    return true
  } catch (e) {
    if ((e as NodeJS.ErrnoException).code === 'ENOENT') return false
    throw new CliError(`Cannot delete ${path}. Check file and directory permissions.`)
  }
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

/**
 * Strips trailing slashes so `${server}/api/...` URLs stay well-formed, and
 * rejects anything that is not an http(s) base URL. Validating here means a
 * bad `--server` fails with a precise usage error instead of surfacing later
 * as `TypeError: Invalid URL` from inside the generated client.
 */
export function normalizeServer(url: string): string {
  const trimmed = url.trim().replace(/\/+$/, '')
  if (parseServerUrl(trimmed) === undefined) {
    throw new CliError(
      `Invalid server URL '${url}'. Expected an http:// or https:// URL, e.g. ${DEFAULT_SERVER}.`,
      EXIT_USAGE,
    )
  }
  return trimmed
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
