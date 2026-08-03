import type { Command } from 'commander'

import { createAuthenticatedClients } from '../client'
import { parseFormat, renderTable } from '../output'
import { folderPaths } from '../resolve'
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
      const active = folderList.filter((f) => f.deletedAt === undefined || f.deletedAt === null)
      const paths = folderPaths(folderList)
      // Sorting by path puts children directly under their parent, which is
      // the only ordering that reads as a tree in a flat list.
      return active
        .map((folder, index) => ({ id: folder.id, path: paths[index] ?? folder.data.name }))
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
