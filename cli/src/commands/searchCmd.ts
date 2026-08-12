import type { Command } from 'commander'

import type { BookmarkJson, TagJson } from '../api'
import { BookmarkJsonToJSON } from '../api'
import { createAuthenticatedClients } from '../client'
import { parseFormat } from '../output'
import { printBookmarkTable } from './bookmarksCmd'
import {
  COLLECTION_FORBIDDEN_MESSAGE,
  effectiveConfig,
  resolveTargetCollectionId,
  withHttpErrors,
} from './commandHelpers'

export interface SearchOptions {
  collection?: string
  format?: string
}

/**
 * Matches a bookmark against every term, all of which must hit somewhere
 * (title, URL or one of its tag names) — plain case-insensitive substrings.
 *
 * Deliberately **not** the web UI's query language. The app supports `#tag`,
 * `under:folder`, `created:>2024` and negation, and its engine is pure
 * TypeScript that this package could in principle import — but only by mapping
 * the frontend's `@/` alias into the CLI's build and inheriting its type
 * settings, which couples the two packages' compilation for a command whose job
 * is "find the link I half-remember". `bookmarks list --tag/--folder` remains
 * the way to filter precisely.
 */
export function matchesQuery(
  bookmark: BookmarkJson,
  terms: string[],
  tagNames: Map<string, string>,
): boolean {
  const haystack = [
    bookmark.data.title,
    bookmark.data.url,
    ...[...(bookmark.data.tagIds ?? [])].map((id) => tagNames.get(id) ?? ''),
  ]
    .join('\n')
    .toLowerCase()
  return terms.every((term) => haystack.includes(term))
}

/** `linkweave search <query...>` */
export async function runSearch(
  query: string[],
  options: SearchOptions,
  cmd: Command,
): Promise<void> {
  const config = effectiveConfig(cmd)
  const format = parseFormat(options.format ?? 'table')
  const clients = createAuthenticatedClients(config)
  const terms = query.map((term) => term.toLowerCase()).filter(Boolean)

  const matches = await withHttpErrors(config, { forbidden: COLLECTION_FORBIDDEN_MESSAGE }, async () => {
    const collectionId = await resolveTargetCollectionId(clients, config, options.collection)
    const [{ bookmarkList }, { tagList }] = await Promise.all([
      clients.bookmarks.apiBookmarksGet({ collectionId }),
      clients.tags.apiTagsGet({ collectionId }),
    ])
    const tagNames = new Map(tagList.map((tag: TagJson) => [tag.id, tag.data.name]))
    return {
      bookmarks: bookmarkList.filter((b) => matchesQuery(b, terms, tagNames)),
      tagNames,
    }
  })

  switch (format) {
    case 'json':
      console.log(JSON.stringify(matches.bookmarks.map(BookmarkJsonToJSON), null, 2))
      break
    case 'ids':
      for (const bookmark of matches.bookmarks) console.log(bookmark.id)
      break
    case 'table':
      // An empty result is not an error — grep says nothing and exits 0 too, and
      // a script piping this wants an empty list rather than a failure.
      if (matches.bookmarks.length === 0) {
        process.stderr.write('No bookmarks matched.\n')
        return
      }
      printBookmarkTable(matches.bookmarks, matches.tagNames)
      break
  }
}
