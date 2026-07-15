import { createInterface } from 'node:readline/promises'
import { Writable } from 'node:stream'

import { createClients } from '../client'
import {
  API_KEY_PATTERN,
  DEFAULT_SERVER,
  configPath,
  loadStoredConfig,
  normalizeServer,
  saveStoredConfig,
} from '../config'
import { ResponseError } from '../api'
import { CliError, EXIT_USAGE, isTlsError, toCliError } from '../errors'

export interface LoginOptions {
  server?: string
  apiKey?: string
}

const KEY_FORMAT_MESSAGE = 'Invalid API key format. Expected: lw_ followed by 64 hex characters.'

async function promptVisible(question: string): Promise<string> {
  const rl = createInterface({ input: process.stdin, output: process.stderr })
  try {
    return (await rl.question(question)).trim()
  } finally {
    rl.close()
  }
}

/**
 * Prompts without echoing the typed/pasted characters, so the API key does
 * not end up on screen or in terminal scrollback.
 */
async function promptHidden(question: string): Promise<string> {
  process.stderr.write(question)
  const muted = new Writable({ write: (_chunk, _encoding, callback) => callback() })
  const rl = createInterface({ input: process.stdin, output: muted, terminal: true })
  try {
    const answer = await rl.question('')
    // The muted stream also swallows the user's Enter keypress.
    process.stderr.write('\n')
    return answer.trim()
  } finally {
    rl.close()
  }
}

/**
 * `linkweave login` (UC-080): collects server URL + API key (flags or
 * interactive prompts), validates the key against GET /auth/me, and stores
 * the config at ~/.linkweave/config.json with 0600 permissions.
 */
export async function runLogin(options: LoginOptions): Promise<void> {
  const stored = loadStoredConfig()
  let server = normalizeServer(
    options.server ?? process.env['LINKWEAVE_SERVER'] ?? stored?.server ?? DEFAULT_SERVER,
  )
  let apiKey = options.apiKey?.trim()

  if (apiKey === undefined) {
    if (!process.stdin.isTTY) {
      throw new CliError(
        'API key required in non-interactive mode. Pass --api-key <key>.',
        EXIT_USAGE,
      )
    }
    if (options.server === undefined) {
      const answer = await promptVisible(`LinkWeave server URL [${server}]: `)
      if (answer) server = normalizeServer(answer)
    }
    for (let attempt = 0; ; attempt++) {
      apiKey = await promptHidden(`API key (created at ${server}/settings/api-keys): `)
      if (API_KEY_PATTERN.test(apiKey)) break
      if (attempt >= 2) throw new CliError(KEY_FORMAT_MESSAGE)
      process.stderr.write(`Error: ${KEY_FORMAT_MESSAGE}\n`)
    }
  } else if (!API_KEY_PATTERN.test(apiKey)) {
    throw new CliError(KEY_FORMAT_MESSAGE, EXIT_USAGE)
  }

  // BR-024: validate against the server before storing anything.
  const clients = createClients(server, apiKey)
  let me
  try {
    me = await clients.auth.apiAuthMeGet()
  } catch (error) {
    if (error instanceof ResponseError && error.response.status === 401) {
      throw new CliError(
        `API key rejected by server. The key may be invalid or revoked. Create a new key at ${server}/settings/api-keys`,
      )
    }
    if (isTlsError(error)) {
      throw new CliError(
        'TLS certificate verification failed. Add --insecure to disable TLS verification for this login.',
      )
    }
    throw await toCliError(error, { server })
  }

  if (stored) {
    process.stderr.write(
      `⚠ Overwriting existing configuration for ${stored.userEmail ?? stored.server}.\n`,
    )
  }

  saveStoredConfig({
    server,
    apiKey,
    userEmail: me.email,
    defaultCollectionId: me.defaultCollectionId,
  })
  console.log(`✓ Logged in as ${me.email}. Configuration saved to ${configPath()}`)
}
