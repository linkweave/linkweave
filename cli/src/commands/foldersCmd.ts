import type { Command } from 'commander'

import { CliError } from '../errors'
import { createAuthenticatedClients } from '../client'
import { parseFormat, renderTable } from '../output'
import {
  findFolder,
  folderPathSegments,
  folderPaths,
  isLiveFolder,
  normalizeFolderPath,
  parentFolderPath,
  resolveFolderId,
} from '../resolve'
import {
  COLLECTION_FORBIDDEN_MESSAGE,
  effectiveConfig,
  resolveTargetCollectionId,
  withHttpErrors,
} from './commandHelpers'

export interface FoldersListOptions {
  collection?: string
  format?: string
}

/** `linkweave folders list` — paths, in the same syntax `--folder` accepts. */
export async function runFoldersList(options: FoldersListOptions, cmd: Command): Promise<void> {
  const config = effectiveConfig(cmd)
  const format = parseFormat(options.format ?? 'table')
  const clients = createAuthenticatedClients(config)

  const folders = await withHttpErrors(
    config,
    { forbidden: COLLECTION_FORBIDDEN_MESSAGE },
    async () => {
      const collectionId = await resolveTargetCollectionId(clients, config, options.collection)
      const { folderList } = await clients.folders.apiFoldersGet({ collectionId })
      // Sorting by path puts children directly under their parent, which is
      // the only ordering that reads as a tree in a flat list.
      return folderPaths(folderList.filter(isLiveFolder))
        .map(({ folder, path }) => ({ id: folder.id, path }))
        .sort((a, b) => a.path.localeCompare(b.path))
    },
  )

  switch (format) {
    case 'json':
      console.log(JSON.stringify(folders, null, 2))
      break
    case 'ids':
      for (const folder of folders) console.log(folder.id)
      break
    case 'table':
      console.log(renderTable(['ID', 'Path'], folders.map((f) => [f.id, f.path])))
      break
  }
}

export interface FoldersMutateOptions {
  collection?: string
}

/** Paths that name the collection root rather than a folder in it. */
const ROOT_PATHS = new Set(['', '/', '.'])

/**
 * `linkweave folders create <path>`
 *
 * Missing parents are created, like `mkdir -p`. Also like `mkdir`, an existing
 * path is an error rather than a silent success — `bookmarks add --folder` is
 * the idempotent way in, and here a second create almost always means a typo.
 */
export async function runFoldersCreate(
  path: string,
  options: FoldersMutateOptions,
  cmd: Command,
): Promise<void> {
  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)
  const wanted = normalizeFolderPath(path)
  if (wanted === '') throw new CliError(`Invalid folder path: '${path}'`)

  await withHttpErrors(config, { forbidden: COLLECTION_FORBIDDEN_MESSAGE }, async () => {
    const collectionId = await resolveTargetCollectionId(clients, config, options.collection)
    const { folderList } = await clients.folders.apiFoldersGet({ collectionId })
    const existing = folderPaths(folderList.filter(isLiveFolder)).find(
      (entry) => entry.path.toLowerCase() === wanted.toLowerCase(),
    )
    if (existing) throw new CliError(`Folder already exists at path '${existing.path}'.`)
    return resolveFolderId(clients.folders, collectionId, wanted, { create: true })
  })
  console.log(`✓ Folder created: ${wanted}`)
}

/** `linkweave folders rename <path> <new-name>` — renames the leaf only. */
export async function runFoldersRename(
  path: string,
  newName: string,
  options: FoldersMutateOptions,
  cmd: Command,
): Promise<void> {
  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)
  // A rename replaces the last segment, so a path here would be ambiguous
  // about whether it also meant to move the folder. `folders mv` does that.
  if (folderPathSegments(newName).length !== 1) {
    throw new CliError(
      `Invalid folder name '${newName}'. A name cannot contain '/' — use 'linkweave folders mv' to move a folder.`,
    )
  }

  const previous = await withHttpErrors(
    config,
    { forbidden: COLLECTION_FORBIDDEN_MESSAGE },
    async () => {
      const collectionId = await resolveTargetCollectionId(clients, config, options.collection)
      const found = await findFolder(clients.folders, collectionId, path)
      await clients.folders.apiFoldersFolderIdPut({
        folderId: found.folder.id,
        // parentId and color are read back and passed through deliberately:
        // the endpoint replaces the whole folder, and treats an absent parent
        // as "move to the root" — so omitting it would quietly re-home the
        // folder and its entire subtree.
        folderSaveJson: {
          collectionId,
          parentId: found.folder.data.parentId,
          name: newName,
          color: found.folder.data.color,
        },
      })
      return found.path
    },
  )
  console.log(`✓ Folder renamed: ${previous} → ${join(parentFolderPath(previous), newName)}`)
}

/** `linkweave folders mv <path> <destination>` — reparents a folder. */
export async function runFoldersMv(
  path: string,
  destination: string,
  options: FoldersMutateOptions,
  cmd: Command,
): Promise<void> {
  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)
  const toRoot = ROOT_PATHS.has(destination.trim()) || normalizeFolderPath(destination) === ''

  const moved = await withHttpErrors(
    config,
    { forbidden: COLLECTION_FORBIDDEN_MESSAGE },
    async () => {
      const collectionId = await resolveTargetCollectionId(clients, config, options.collection)
      const found = await findFolder(clients.folders, collectionId, path)
      const parent = toRoot
        ? undefined
        : await findFolder(clients.folders, collectionId, destination)
      // Caught here for a usable message; the server also rejects it, but as a
      // generic validation failure that does not say which folder was at fault.
      if (parent?.folder.id === found.folder.id) {
        throw new CliError(`A folder cannot be moved into itself ('${found.path}').`)
      }
      if (parent && `${parent.path}/`.startsWith(`${found.path}/`)) {
        throw new CliError(
          `Cannot move '${found.path}' into its own subfolder '${parent.path}'.`,
        )
      }
      await clients.folders.apiFoldersFolderIdMovePatch({
        folderId: found.folder.id,
        folderMoveJson: { collectionId, parentId: parent?.folder.id },
      })
      return { from: found.path, to: join(parent?.path ?? '', found.folder.data.name) }
    },
  )
  console.log(`✓ Folder moved: ${moved.from} → ${moved.to}`)
}

/** `linkweave folders rm <path>` — soft-deletes the folder and its contents. */
export async function runFoldersRm(
  path: string,
  options: FoldersMutateOptions,
  cmd: Command,
): Promise<void> {
  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)

  const removed = await withHttpErrors(
    config,
    { forbidden: COLLECTION_FORBIDDEN_MESSAGE },
    async () => {
      const collectionId = await resolveTargetCollectionId(clients, config, options.collection)
      const found = await findFolder(clients.folders, collectionId, path)
      await clients.folders.apiFoldersFolderIdDelete({ folderId: found.folder.id })
      return found
    },
  )
  // No prompt, and none is needed: this cascades to sub-folders and to the
  // bookmarks inside, but all of it is a soft delete that `trash restore`
  // undoes — the same bargain `bookmarks rm` makes.
  console.log(
    `✓ Folder removed: ${removed.path}\n` +
      `  It and its contents are in the trashbin; restore with 'linkweave trash restore ${removed.folder.id}'.`,
  )
}

/** Joins a parent path and a leaf name, tolerating an empty (root) parent. */
function join(parentPath: string, name: string): string {
  return parentPath === '' ? name : `${parentPath}/${name}`
}
