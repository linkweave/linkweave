import type { Command } from 'commander'

import type { BookmarkJson } from '../api'
import { BookmarkJsonToJSON } from '../api'
import { createAuthenticatedClients } from '../client'
import { CliError, EXIT_USAGE } from '../errors'
import { parseFormat, renderTable } from '../output'
import { parseTagNames, resolveFolderId, resolveTagIds } from '../resolve'
import {
  COLLECTION_FORBIDDEN_MESSAGE,
  effectiveConfig,
  resolveTargetCollectionId,
  withHttpErrors,
} from './shared'

export interface BookmarkAddOptions {
  title?: string
  collection?: string
  folder?: string
  tags?: string
  description?: string
}

/** `linkweave bookmarks add <url>` */
export async function runBookmarksAdd(
  url: string,
  options: BookmarkAddOptions,
  cmd: Command,
): Promise<void> {
  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)

  const created = await withHttpErrors(
    config,
    { forbidden: COLLECTION_FORBIDDEN_MESSAGE },
    async () => {
      const collectionId = await resolveTargetCollectionId(clients, config, options.collection)
      const tagIds = options.tags
        ? await resolveTagIds(clients.tags, collectionId, parseTagNames(options.tags))
        : undefined
      const folderId = options.folder
        ? await resolveFolderId(clients.folders, collectionId, options.folder, { create: true })
        : undefined
      return clients.bookmarks.apiBookmarksPost({
        bookmarkSaveJson: {
          collectionId,
          folderId,
          // The API does not auto-fetch page titles; fall back to the URL.
          title: options.title ?? url,
          url,
          description: options.description,
          tagIds: tagIds ? new Set(tagIds) : undefined,
        },
      })
    },
  )
  console.log(`✓ Bookmark created: ${created.data.title} (${created.data.url})`)
}

export interface BookmarkListOptions {
  collection?: string
  folder?: string
  tag?: string
  format?: string
}

/** `linkweave bookmarks list` */
export async function runBookmarksList(options: BookmarkListOptions, cmd: Command): Promise<void> {
  const config = effectiveConfig(cmd)
  const format = parseFormat(options.format ?? 'table')
  const clients = createAuthenticatedClients(config)

  const bookmarks = await withHttpErrors(
    config,
    { forbidden: COLLECTION_FORBIDDEN_MESSAGE },
    async () => {
      const collectionId = await resolveTargetCollectionId(clients, config, options.collection)
      let { bookmarkList } = await clients.bookmarks.apiBookmarksGet({ collectionId })

      // The API only filters by collection; folder and tag filters are local.
      if (options.folder) {
        const folderId = await resolveFolderId(clients.folders, collectionId, options.folder, {
          create: false,
        })
        bookmarkList = bookmarkList.filter((b) => b.data.folderId === folderId)
      }
      if (options.tag) {
        const { tagList } = await clients.tags.apiTagsGet({ collectionId })
        const needle = options.tag.toLowerCase()
        const tag = tagList.find((t) => t.data.name.toLowerCase() === needle)
        if (!tag) {
          throw new CliError(`No tag found with name '${options.tag}' in the collection.`)
        }
        bookmarkList = bookmarkList.filter((b) => b.data.tagIds?.has(tag.id))
      }

      if (format === 'table') {
        const { tagList } = await clients.tags.apiTagsGet({ collectionId })
        const tagNames = new Map(tagList.map((t) => [t.id, t.data.name]))
        printBookmarkTable(bookmarkList, tagNames)
        return undefined
      }
      return bookmarkList
    },
  )

  if (bookmarks === undefined) return
  if (format === 'json') {
    console.log(JSON.stringify(bookmarks.map(BookmarkJsonToJSON), null, 2))
  } else {
    for (const bookmark of bookmarks) console.log(bookmark.id)
  }
}

function printBookmarkTable(bookmarks: BookmarkJson[], tagNames: Map<string, string>): void {
  console.log(
    renderTable(
      ['ID', 'Title', 'URL', 'Tags'],
      bookmarks.map((b) => [
        b.id,
        b.data.title,
        b.data.url,
        [...(b.data.tagIds ?? [])].map((id) => tagNames.get(id) ?? id).join(', '),
      ]),
    ),
  )
}

export interface BookmarkEditOptions {
  title?: string
  url?: string
  description?: string
  tags?: string
}

/** `linkweave bookmarks edit <id>` */
export async function runBookmarksEdit(
  bookmarkId: string,
  options: BookmarkEditOptions,
  cmd: Command,
): Promise<void> {
  if (
    options.title === undefined &&
    options.url === undefined &&
    options.description === undefined &&
    options.tags === undefined
  ) {
    throw new CliError(
      'Nothing to update. Provide at least one of --title, --url, --description, --tags.',
      EXIT_USAGE,
    )
  }

  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)

  const updated = await withHttpErrors(
    config,
    {
      forbidden: COLLECTION_FORBIDDEN_MESSAGE,
      notFound: `Bookmark not found: ${bookmarkId}`,
    },
    async () => {
      const existing = await clients.bookmarks.apiBookmarksBookmarkIdGet({ bookmarkId })
      const collectionId = existing.data.collectionId
      const tagIds = options.tags
        ? new Set(await resolveTagIds(clients.tags, collectionId, parseTagNames(options.tags)))
        : existing.data.tagIds
      return clients.bookmarks.apiBookmarksBookmarkIdPut({
        bookmarkId,
        bookmarkSaveJson: {
          collectionId,
          folderId: existing.data.folderId,
          title: options.title ?? existing.data.title,
          url: options.url ?? existing.data.url,
          description: options.description ?? existing.data.description,
          tagIds,
        },
      })
    },
  )
  console.log(`✓ Bookmark updated: ${updated.data.title}`)
}

/** `linkweave bookmarks rm <id>` — soft-deletes (moves to the trashbin). */
export async function runBookmarksRm(bookmarkId: string, cmd: Command): Promise<void> {
  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)

  await withHttpErrors(
    config,
    { notFound: `Bookmark not found: ${bookmarkId}`, forbidden: 'Access denied.' },
    () => clients.bookmarks.apiBookmarksBookmarkIdDelete({ bookmarkId }),
  )
  console.log(`✓ Bookmark removed: ${bookmarkId}`)
}
