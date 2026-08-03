import { createInterface } from 'node:readline/promises'

import type { Command } from 'commander'

import type { ApiClients } from '../client'
import type { EffectiveConfig } from '../config'
import { loadStoredConfig, resolveEffectiveConfig } from '../config'
import type { HttpErrorContext } from '../errors'
import { CliError, EXIT_USAGE, toCliError } from '../errors'
import { resolveCollectionId } from '../resolve'

/** UC-079 A4 message for HTTP 403 on collection-scoped operations. */
export const COLLECTION_FORBIDDEN_MESSAGE =
  "Collection not found or access denied. Use 'linkweave collections list' to see your collections."

export interface GlobalOptions {
  server?: string
  apiKey?: string
  insecure?: boolean
}

export function effectiveConfig(cmd: Command): EffectiveConfig {
  const options = cmd.optsWithGlobals<GlobalOptions>()
  return resolveEffectiveConfig(
    { server: options.server, apiKey: options.apiKey },
    process.env,
    loadStoredConfig(),
  )
}

/** Runs an API interaction, translating failures to user-facing CliErrors. */
export async function withHttpErrors<T>(
  config: EffectiveConfig,
  context: Omit<HttpErrorContext, 'server'>,
  fn: () => Promise<T>,
): Promise<T> {
  try {
    return await fn()
  } catch (error) {
    throw await toCliError(error, { server: config.server, ...context })
  }
}

/**
 * Determines the collection to operate on: an explicit `--collection` value
 * (ID or name), else the default collection stored at login, else the
 * server-side default from GET /auth/me.
 */
export async function resolveTargetCollectionId(
  clients: ApiClients,
  config: EffectiveConfig,
  spec: string | undefined,
): Promise<string> {
  if (spec) return resolveCollectionId(clients.collections, spec)
  if (config.defaultCollectionId) return config.defaultCollectionId
  const me = await clients.auth.apiAuthMeGet()
  return me.defaultCollectionId
}

/**
 * Gates an irreversible action behind a confirmation.
 *
 * `--yes` skips the prompt, for scripts. Without a TTY there is nobody to ask,
 * so this refuses rather than assuming consent — silently purging because
 * stdin happened to be a pipe is the wrong default for a destructive command.
 */
export async function confirmIrreversible(question: string, assumeYes: boolean): Promise<void> {
  if (assumeYes) return
  if (!process.stdin.isTTY) {
    throw new CliError(
      `${question} Refusing in non-interactive mode — pass --yes to confirm.`,
      EXIT_USAGE,
    )
  }
  const rl = createInterface({ input: process.stdin, output: process.stderr })
  try {
    const answer = (await rl.question(`${question} [y/N]: `)).trim().toLowerCase()
    if (answer !== 'y' && answer !== 'yes') throw new CliError('Aborted.')
  } finally {
    rl.close()
  }
}
