import { readFileSync, writeFileSync } from 'node:fs'
import { basename } from 'node:path'

import type { Command } from 'commander'

import type { BookmarkJson } from '../api'
import { BookmarkJsonToJSON } from '../api'
import { createAuthenticatedClients } from '../client'
import { CliError, EXIT_USAGE } from '../errors'
import { parseFormat, renderTable } from '../output'
import { folderPaths, parseTagNames, resolveFolderId, resolveTagIds } from '../resolve'
import {
  COLLECTION_FORBIDDEN_MESSAGE,
  effectiveConfig,
  resolveTargetCollectionId,
  withHttpErrors,
} from './commandHelpers'

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
      // `!== undefined`, not truthiness: `--tags ''` means "no tags", which is
      // a different request from omitting the flag entirely.
      const tagIds =
        options.tags !== undefined
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
          tagIds: tagIds && new Set(tagIds),
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

      // Fetched once and shared: --tag resolves a name to an ID with it, and
      // the table renders its tag-name column from it.
      const needsTags = options.tag !== undefined || format === 'table'
      const tagList = needsTags ? (await clients.tags.apiTagsGet({ collectionId })).tagList : []

      // The API only filters by collection; folder and tag filters are local.
      if (options.folder) {
        const folderId = await resolveFolderId(clients.folders, collectionId, options.folder, {
          create: false,
        })
        bookmarkList = bookmarkList.filter((b) => b.data.folderId === folderId)
      }
      if (options.tag) {
        const needle = options.tag.toLowerCase()
        const tag = tagList.find((t) => t.data.name.toLowerCase() === needle)
        if (!tag) {
          throw new CliError(`No tag found with name '${options.tag}' in the collection.`)
        }
        bookmarkList = bookmarkList.filter((b) => b.data.tagIds?.has(tag.id))
      }

      if (format === 'table') {
        printBookmarkTable(bookmarkList, new Map(tagList.map((t) => [t.id, t.data.name])))
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

/** Shared with `search`, so both render a bookmark list identically. */
export function printBookmarkTable(bookmarks: BookmarkJson[], tagNames: Map<string, string>): void {
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
      // `--tags ''` clears every tag; omitting the flag keeps the current set.
      const tagIds =
        options.tags !== undefined
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

export interface BookmarkShowOptions {
  format?: string
}

/** `linkweave bookmarks show <id>` — every field of one bookmark. */
export async function runBookmarksShow(
  bookmarkId: string,
  options: BookmarkShowOptions,
  cmd: Command,
): Promise<void> {
  const config = effectiveConfig(cmd)
  const format = parseFormat(options.format ?? 'table')
  const clients = createAuthenticatedClients(config)

  const shown = await withHttpErrors(
    config,
    {
      forbidden: COLLECTION_FORBIDDEN_MESSAGE,
      notFound: `Bookmark not found: ${bookmarkId}`,
    },
    async () => {
      const bookmark = await clients.bookmarks.apiBookmarksBookmarkIdGet({ bookmarkId })
      if (format !== 'table') return { bookmark, tagNames: [] as string[], folderPath: '' }

      // Only the table resolves names: json is the raw payload, and ids prints
      // one line. Both would be paying for two round trips they never show.
      const collectionId = bookmark.data.collectionId
      const { tagList } = await clients.tags.apiTagsGet({ collectionId })
      const byId = new Map(tagList.map((tag) => [tag.id, tag.data.name]))
      const tagNames = [...(bookmark.data.tagIds ?? [])]
        .map((id) => byId.get(id) ?? id)
        .sort((a, b) => a.localeCompare(b))

      let folderPath = ''
      if (bookmark.data.folderId !== undefined) {
        const { folderList } = await clients.folders.apiFoldersGet({ collectionId })
        folderPath =
          folderPaths(folderList).find((entry) => entry.folder.id === bookmark.data.folderId)
            ?.path ?? ''
      }
      return { bookmark, tagNames, folderPath }
    },
  )

  switch (format) {
    case 'json':
      console.log(JSON.stringify(BookmarkJsonToJSON(shown.bookmark), null, 2))
      break
    case 'ids':
      console.log(shown.bookmark.id)
      break
    case 'table':
      printBookmarkDetail(shown.bookmark, shown.tagNames, shown.folderPath)
      break
  }
}

/** Field/value rows rather than columns: one record reads better down the page. */
function printBookmarkDetail(
  bookmark: BookmarkJson,
  tagNames: string[],
  folderPath: string,
): void {
  const { data, entityInfo } = bookmark
  const rows: string[][] = [
    ['ID', bookmark.id],
    ['Title', data.title],
    ['URL', data.url],
    ['Description', data.description ?? ''],
    ['Collection', data.collectionId],
    ['Folder', folderPath],
    ['Tags', tagNames.join(', ')],
    ['Clicks', String(bookmark.clickCount)],
    ['Last clicked', formatTimestamp(bookmark.lastClickedAt)],
    ['Created', formatTimestamp(entityInfo.timestampErstellt)],
    ['Updated', formatTimestamp(entityInfo.timestampMutiert)],
  ]
  console.log(renderTable(['Field', 'Value'], rows))
}

/** Minute precision: seconds are noise when reading a single record. */
function formatTimestamp(value: Date | undefined): string {
  if (value === undefined) return ''
  return value.toISOString().slice(0, 16).replace('T', ' ')
}

export interface BookmarkExportOptions {
  collection?: string
  output?: string
}

/**
 * `linkweave bookmarks export` — the collection as a Netscape bookmark file,
 * the same HTML every browser imports.
 */
export async function runBookmarksExport(
  options: BookmarkExportOptions,
  cmd: Command,
): Promise<void> {
  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)

  const html = await withHttpErrors(config, { forbidden: COLLECTION_FORBIDDEN_MESSAGE }, async () => {
    const collectionId = await resolveTargetCollectionId(clients, config, options.collection)
    // The endpoint produces text/html, which the generator maps to a void
    // response — so the typed call would discard exactly what we came for.
    // The raw variant still hands back the Response.
    const response = await clients.export.apiCollectionsCollectionIdExportGetRaw({ collectionId })
    return response.raw.text()
  })

  if (options.output === undefined) {
    // Straight to stdout so it can be piped or redirected (BR-018).
    process.stdout.write(html)
    return
  }
  try {
    writeFileSync(options.output, html)
  } catch {
    throw new CliError(`Cannot write to ${options.output}. Check the path and permissions.`)
  }
  console.log(`✓ Exported to ${options.output}`)
}

export interface BookmarkImportOptions {
  collection?: string
}

/** The server's own cap; checked here so a big file fails before the upload. */
const IMPORT_MAX_BYTES = 5 * 1024 * 1024

/** `linkweave bookmarks import <file>` */
export async function runBookmarksImport(
  file: string,
  options: BookmarkImportOptions,
  cmd: Command,
): Promise<void> {
  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)

  const name = basename(file)
  const extension = /\.(html?)$/i.exec(name)
  if (extension === null) {
    throw new CliError(
      `'${name}' is not a bookmarks HTML file. Export one from your browser (or 'linkweave bookmarks export') and pass the .html file.`,
      EXIT_USAGE,
    )
  }

  let bytes: Buffer
  try {
    bytes = readFileSync(file)
  } catch {
    throw new CliError(`Cannot read ${file}. Check the path and permissions.`)
  }
  if (bytes.byteLength === 0) throw new CliError(`${file} is empty.`)
  if (bytes.byteLength > IMPORT_MAX_BYTES) {
    throw new CliError(
      `${file} is ${Math.round(bytes.byteLength / 1024 / 1024)} MB; the server accepts at most 5 MB.`,
    )
  }

  const summary = await withHttpErrors(
    config,
    { forbidden: COLLECTION_FORBIDDEN_MESSAGE },
    async () => {
      const collectionId = await resolveTargetCollectionId(clients, config, options.collection)
      return clients.import.apiCollectionsCollectionIdImportPost({
        collectionId,
        // A File, not a bare Blob: the generated client appends it to the form
        // without a filename argument, so the name has to travel on the object
        // itself or the server sees none and rejects the upload. The server
        // matches the extension case-sensitively while recording the name as
        // the bookmarks' import source, so only the extension is lowercased —
        // the stem is left as the user wrote it.
        file: new File([new Uint8Array(bytes)], name.slice(0, -extension[0].length) + extension[0].toLowerCase(), {
          type: 'text/html',
        }),
      })
    },
  )
  console.log(
    `✓ Imported ${summary.bookmarksCreated} bookmark(s) into ${summary.foldersCreated} new folder(s).` +
      (summary.bookmarksSkipped > 0
        ? `\n  ${summary.bookmarksSkipped} skipped as duplicates of bookmarks already in the collection.`
        : ''),
  )
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
