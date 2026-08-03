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

  const collectionId = collectionSpec
    ? await resolveCollectionId(clients.collections, collectionSpec)
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
  } catch {
    return
  }

  const needle = (prefix ?? '').toLowerCase()
  const matches = candidates.filter((value) => value.toLowerCase().startsWith(needle))
  if (matches.length > 0) process.stdout.write(matches.join('\n') + '\n')
}
