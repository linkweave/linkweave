import type { Command } from 'commander'

import type { ApiClients } from '../client'
import { createAuthenticatedClients } from '../client'
import { readCached, writeCached } from '../cache'
import type { FolderJson } from '../api'
import { configPath, loadStoredConfig, resolveEffectiveConfig } from '../config'
import { resolveCollectionId } from '../resolve'
import type { GlobalOptions } from './commandHelpers'

/** Value sets the shell scripts can ask for. */
export const COMPLETION_SOURCES = ['collections', 'tags', 'folders'] as const

export type CompletionSource = (typeof COMPLETION_SOURCES)[number]

/**
 * A completion request must never leave the shell hanging. 1.5s is long
 * enough for a warm server and short enough that a dead one is not painful;
 * the cache means the timeout is rarely reached twice in a row.
 */
const REQUEST_TIMEOUT_MS = 1500

export interface CompleteOptions {
  /** Collection ID or name scoping `tags`/`folders`, from the command line. */
  collection?: string
}

/**
 * Builds every folder's full path (`Dev/TypeScript`), so `--folder` completes
 * against the same syntax the flag accepts.
 */
function folderPaths(folderList: FolderJson[]): string[] {
  const active = folderList.filter((f) => f.deletedAt === undefined || f.deletedAt === null)
  const byId = new Map(active.map((folder) => [folder.id, folder]))
  return active.map((folder) => {
    const segments: string[] = []
    let current: FolderJson | undefined = folder
    // The guard is against a parent cycle in server data: a completion helper
    // that spins forever would wedge the user's shell.
    const seen = new Set<string>()
    while (current !== undefined && !seen.has(current.id)) {
      seen.add(current.id)
      segments.unshift(current.data.name)
      const parentId: string | undefined = current.data.parentId
      current = parentId === undefined ? undefined : byId.get(parentId)
    }
    return segments.join('/')
  })
}

async function fetchCandidates(
  source: CompletionSource,
  clients: ApiClients,
  collectionSpec: string | undefined,
  defaultCollectionId: string | undefined,
  signal: AbortSignal,
): Promise<string[]> {
  if (source === 'collections') {
    const { collections } = await clients.collections.apiCollectionsGet({ signal })
    return collections.map((collection) => collection.name)
  }

  // Every request below shares the one signal, so the deadline covers the
  // whole completion rather than resetting per call — including this lookup,
  // which is a second round trip whenever --collection is given by name.
  const collectionId = collectionSpec
    ? await resolveCollectionId(clients.collections, collectionSpec, { signal })
    : (defaultCollectionId ?? (await clients.auth.apiAuthMeGet({ signal })).defaultCollectionId)

  if (source === 'tags') {
    const { tagList } = await clients.tags.apiTagsGet({ collectionId }, { signal })
    return tagList.map((tag) => tag.data.name)
  }
  const { folderList } = await clients.folders.apiFoldersGet({ collectionId }, { signal })
  return folderPaths(folderList)
}

/**
 * `linkweave __complete <source> [prefix]` — the hidden callback the generated
 * shell scripts invoke to complete option *values*, which the static script
 * cannot know. Prints one candidate per line.
 *
 * It is deliberately total: any failure (not logged in, offline, revoked key,
 * unreadable config) prints nothing and exits 0. A completion helper that
 * reports an error corrupts the command line the user is in the middle of
 * typing, which is far worse than offering no suggestions.
 */
export async function runComplete(
  source: CompletionSource,
  prefix: string | undefined,
  options: CompleteOptions,
  cmd: Command,
): Promise<void> {
  let candidates: string[] = []
  try {
    const globals = cmd.optsWithGlobals<GlobalOptions>()
    const config = resolveEffectiveConfig(
      { server: globals.server, apiKey: globals.apiKey },
      process.env,
      loadStoredConfig(configPath(), () => {}),
    )
    const key = `${config.server}|${source}|${options.collection ?? ''}`
    const cached = readCached(key)
    if (cached !== undefined) {
      candidates = cached
    } else {
      const clients = createAuthenticatedClients(config)
      candidates = await fetchCandidates(
        source,
        clients,
        options.collection,
        config.defaultCollectionId,
        AbortSignal.timeout(REQUEST_TIMEOUT_MS),
      )
      writeCached(key, candidates)
    }
  } catch (error) {
    // Silent by design, and exiting here matters most: this is the path a
    // hung or unreachable server takes, which is exactly when a lingering
    // socket would keep the shell waiting.
    debug(error)
    return finish('')
  }

  const needle = (prefix ?? '').toLowerCase()
  const matches = candidates.filter((value) => value.toLowerCase().startsWith(needle))
  finish(matches.length > 0 ? matches.join('\n') + '\n' : '')
}

/**
 * The catch above is total so that a failure can never corrupt the command
 * line the user is mid-way through typing. The cost is that it hides real
 * bugs as well as expected ones: if the server's payload stops matching the
 * checked-in generated client, the deserialiser throws a TypeError and the
 * only symptom here is "no suggestions".
 *
 * LINKWEAVE_DEBUG surfaces the cause without changing the default. The
 * generated shell scripts send stderr to /dev/null, so run the command
 * directly to see it:
 *
 *     LINKWEAVE_DEBUG=1 linkweave __complete tags
 *
 * Note that the same drift is *not* silent elsewhere — an unrecognised error
 * reaches the user through toCliError, so `linkweave collections list` fails
 * loudly with the same underlying message.
 */
function debug(error: unknown): void {
  if (!process.env['LINKWEAVE_DEBUG']) return
  const detail = error instanceof Error ? (error.stack ?? error.message) : String(error)
  process.stderr.write(`linkweave __complete failed: ${detail}\n`)
}

/**
 * Writes the candidates and exits, rather than returning and letting the event
 * loop drain.
 *
 * Aborting a request settles our promise on time, but undici keeps the
 * underlying connect attempt as an active handle until its own ~10s connect
 * timeout. Against an unreachable server that means the process lingers long
 * past REQUEST_TIMEOUT_MS with nothing left to do — and the shell, which is
 * blocked on this command's output, stays wedged for the whole 10s. Exiting
 * from the write callback keeps the 1.5s budget real while still guaranteeing
 * the bytes are flushed first (stdout is a pipe here, so writes are async).
 */
function finish(text: string): void {
  if (text === '') process.exit(0)
  process.stdout.write(text, () => process.exit(0))
}
