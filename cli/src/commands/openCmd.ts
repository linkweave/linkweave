import { spawn } from 'node:child_process'

import type { Command } from 'commander'

import type { ApiClients } from '../client'
import type { BookmarkJson, TagJson } from '../api'
import { createAuthenticatedClients } from '../client'
import { CliError } from '../errors'
import { looksLikeId } from '../resolve'
import { matchesQuery } from './searchCmd'
import {
  COLLECTION_FORBIDDEN_MESSAGE,
  effectiveConfig,
  resolveTargetCollectionId,
  withHttpErrors,
} from './commandHelpers'

export interface OpenOptions {
  collection?: string
  print?: boolean
}

/** How many candidates to name when a query is ambiguous, before "and N more". */
const AMBIGUITY_SAMPLE = 5

/**
 * The platform's "open this URL in whatever handles it" command. Exported for
 * tests: two of the three branches run on platforms this project is not
 * developed on, so a wrong argv would only surface as a silent failure on
 * someone else's machine.
 */
export function browserCommand(url: string): { command: string; args: string[] } {
  switch (process.platform) {
    case 'darwin':
      return { command: 'open', args: [url] }
    case 'win32':
      // Through cmd, whose `start` treats a quoted first argument as a window
      // title — hence the empty one before the URL.
      return { command: 'cmd', args: ['/c', 'start', '', url] }
    default:
      return { command: 'xdg-open', args: [url] }
  }
}

/**
 * Hands the URL to the desktop and returns without waiting: the browser
 * outlives this process, so the child is detached and its streams ignored —
 * otherwise the CLI would sit there holding a pipe for a window the user is
 * already reading.
 */
export function openInBrowser(url: string): void {
  const { command, args } = browserCommand(url)
  const child = spawn(command, args, { detached: true, stdio: 'ignore' })
  child.on('error', () => {
    process.stderr.write(`Could not run '${command}'. Use --print to get the URL instead.\n`)
  })
  child.unref()
}

/**
 * Resolves what the user typed to exactly one bookmark: an ID is taken as-is,
 * anything else is searched for. Ambiguity is an error rather than a guess —
 * opening the wrong page is a silent failure the user only notices later.
 */
async function resolveBookmark(
  clients: ApiClients,
  collectionId: string,
  target: string[],
): Promise<BookmarkJson> {
  const spec = target.join(' ')
  if (target.length === 1 && looksLikeId(spec)) {
    return clients.bookmarks.apiBookmarksBookmarkIdGet({ bookmarkId: spec })
  }

  const [{ bookmarkList }, { tagList }] = await Promise.all([
    clients.bookmarks.apiBookmarksGet({ collectionId }),
    clients.tags.apiTagsGet({ collectionId }),
  ])
  const tagNames = new Map(tagList.map((tag: TagJson) => [tag.id, tag.data.name]))
  const terms = target.map((term) => term.toLowerCase()).filter(Boolean)
  const matches = bookmarkList.filter((b) => matchesQuery(b, terms, tagNames))

  if (matches.length === 1) return matches[0]!
  if (matches.length === 0) {
    throw new CliError(`No bookmark matched '${spec}'.`)
  }
  const sample = matches
    .slice(0, AMBIGUITY_SAMPLE)
    .map((b) => `  ${b.id}  ${b.data.title}`)
    .join('\n')
  const more = matches.length > AMBIGUITY_SAMPLE ? `\n  …and ${matches.length - AMBIGUITY_SAMPLE} more` : ''
  throw new CliError(
    `'${spec}' matches ${matches.length} bookmarks. Narrow it down or pass an ID:\n${sample}${more}`,
  )
}

/** `linkweave open <bookmark...>` */
export async function runOpen(target: string[], options: OpenOptions, cmd: Command): Promise<void> {
  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)

  const bookmark = await withHttpErrors(config, { forbidden: COLLECTION_FORBIDDEN_MESSAGE }, async () => {
    const collectionId = await resolveTargetCollectionId(clients, config, options.collection)
    return resolveBookmark(clients, collectionId, target)
  })

  if (options.print) {
    // Printing is not opening: no click is recorded, because the user may well
    // be piping the URL somewhere that never visits it.
    console.log(bookmark.data.url)
    return
  }

  openInBrowser(bookmark.data.url)
  process.stderr.write(`Opening ${bookmark.data.title}\n`)

  // Same bookkeeping the web UI does when you click a bookmark, so opening from
  // a terminal does not quietly make "never opened" and click counts wrong.
  // Never fatal: the page is already open, and failing here would report an
  // error for something that worked.
  try {
    await clients.bookmarks.apiBookmarksBookmarkIdTrackClickPost({ bookmarkId: bookmark.id })
  } catch {
    /* the bookmark opened; the counter is not worth an error */
  }
}
