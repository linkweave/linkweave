import type { Command } from 'commander'

import type { ApiClients } from '../client'
import { createAuthenticatedClients } from '../client'
import { CliError } from '../errors'
import { parseFormat, renderTable } from '../output'
import { folderPaths } from '../resolve'
import type { NoOptions } from './commandHelpers'
import { confirmIrreversible, effectiveConfig, withHttpErrors } from './commandHelpers'

/** Errors that need no ID in the message — listing and emptying. */
const TRASH_ERRORS = {
  forbidden: 'Access denied. Your API key may no longer have access to this collection.',
} as const

/**
 * findInTrash validates the ID before restore/purge run, so a 404 from the
 * endpoint itself means the item left the trash in between — another session
 * restored or purged it. Saying that is more use than "Not found (HTTP 404)."
 */
function raceErrors(id: string): { notFound: string; forbidden: string } {
  return {
    notFound: `'${id}' is no longer in the trash — another session may have restored or purged it.`,
    ...TRASH_ERRORS,
  }
}

/**
 * One row of the trashbin. The API returns bookmarks and folders as separate
 * lists; the CLI shows one table because a user thinks of the trash as a
 * single place, and because restore takes an ID without caring which it is.
 */
interface TrashedItem {
  kind: 'bookmark' | 'folder'
  id: string
  label: string
  deletedAt?: Date
}

async function fetchTrash(clients: ApiClients): Promise<TrashedItem[]> {
  const { bookmarks, folders } = await clients.trash.apiTrashbinGet()
  return [
    ...bookmarks.map((b): TrashedItem => ({
      kind: 'bookmark',
      id: b.id,
      label: b.data.title,
      deletedAt: b.deletedAt,
    })),
    // Passed as-is: paths are reconstructed from the trashed set alone, so a
    // folder whose parent is still live shows its own name rather than a full
    // path. folderPaths filters nothing, so these survive despite being
    // soft-deleted — which is the whole point here.
    ...folderPaths(folders).map(({ folder, path }): TrashedItem => ({
      kind: 'folder',
      id: folder.id,
      label: path,
      deletedAt: folder.deletedAt,
    })),
  ].sort((a, b) => (b.deletedAt?.getTime() ?? 0) - (a.deletedAt?.getTime() ?? 0))
}

/** Locates an ID in the trash so restore/purge can pick the right endpoint. */
async function findInTrash(clients: ApiClients, id: string): Promise<TrashedItem> {
  const item = (await fetchTrash(clients)).find((entry) => entry.id === id)
  if (!item) {
    throw new CliError(
      `Nothing with ID '${id}' is in the trash. Use 'linkweave trash list' to see what is.`,
    )
  }
  return item
}

export interface TrashListOptions {
  format?: string
}

/** `linkweave trash list` */
export async function runTrashList(options: TrashListOptions, cmd: Command): Promise<void> {
  const config = effectiveConfig(cmd)
  const format = parseFormat(options.format ?? 'table')
  const clients = createAuthenticatedClients(config)

  const items = await withHttpErrors(config, TRASH_ERRORS, () => fetchTrash(clients))

  switch (format) {
    case 'json':
      console.log(JSON.stringify(items, null, 2))
      break
    case 'ids':
      for (const item of items) console.log(item.id)
      break
    case 'table':
      if (items.length === 0) {
        console.log('The trash is empty.')
        break
      }
      console.log(
        renderTable(
          ['Type', 'ID', 'Name', 'Deleted'],
          items.map((item) => [
            item.kind,
            item.id,
            item.label,
            item.deletedAt?.toISOString().slice(0, 16).replace('T', ' ') ?? '',
          ]),
        ),
      )
      break
  }
}

/** `linkweave trash restore <id>` — works for a bookmark or a folder. */
export async function runTrashRestore(id: string, _options: NoOptions, cmd: Command): Promise<void> {
  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)

  const item = await withHttpErrors(config, raceErrors(id), async () => {
    const found = await findInTrash(clients, id)
    if (found.kind === 'bookmark') {
      await clients.trash.apiTrashbinBookmarksBookmarkIdRestorePost({ bookmarkId: id })
    } else {
      await clients.trash.apiTrashbinFoldersFolderIdRestorePost({ folderId: id })
    }
    return found
  })
  console.log(`✓ Restored ${item.kind}: ${item.label}`)
}

export interface TrashPurgeOptions {
  yes?: boolean
}

/** `linkweave trash purge <id>` — permanent, unlike `bookmarks rm`. */
export async function runTrashPurge(
  id: string,
  options: TrashPurgeOptions,
  cmd: Command,
): Promise<void> {
  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)

  const item = await withHttpErrors(config, raceErrors(id), () => findInTrash(clients, id))
  await confirmIrreversible(
    `Permanently delete ${item.kind} '${item.label}'? This cannot be undone.`,
    options.yes === true,
  )
  await withHttpErrors(config, raceErrors(id), () =>
    item.kind === 'bookmark'
      ? clients.trash.apiTrashbinBookmarksBookmarkIdDelete({ bookmarkId: id })
      : clients.trash.apiTrashbinFoldersFolderIdDelete({ folderId: id }),
  )
  console.log(`✓ Permanently deleted ${item.kind}: ${item.label}`)
}

/** `linkweave trash empty` — permanently deletes everything in the trash. */
export async function runTrashEmpty(options: TrashPurgeOptions, cmd: Command): Promise<void> {
  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)

  const { count } = await withHttpErrors(config, TRASH_ERRORS, () =>
    clients.trash.apiTrashbinCountGet(),
  )
  if (count === 0) {
    console.log('The trash is already empty.')
    return
  }
  // The count is a snapshot, not a promise. The endpoint empties the trash
  // unconditionally and there is no compare-and-swap to bound it to a number,
  // so anything trashed while the prompt waits is destroyed too. Saying
  // "everything" — with the count as context — is what actually happens;
  // "delete all 2 items" would be asking consent for a figure we cannot hold.
  await confirmIrreversible(
    `Permanently delete everything in the trash? That is ${count} item(s) right now, and cannot be undone.`,
    options.yes === true,
  )
  await withHttpErrors(config, TRASH_ERRORS, () => clients.trash.apiTrashbinDelete())
  // No figure here either: the pre-count may not be what was destroyed.
  console.log('✓ Trash emptied.')
}
