import type { Command } from 'commander'

import { createAuthenticatedClients } from '../client'
import { parseFormat, renderTable } from '../output'
import { findTag } from '../resolve'
import {
  COLLECTION_FORBIDDEN_MESSAGE,
  confirmIrreversible,
  effectiveConfig,
  resolveTargetCollectionId,
  withHttpErrors,
} from './commandHelpers'

export interface TagsListOptions {
  collection?: string
  format?: string
}

/** `linkweave tags list` */
export async function runTagsList(options: TagsListOptions, cmd: Command): Promise<void> {
  const config = effectiveConfig(cmd)
  const format = parseFormat(options.format ?? 'table')
  const clients = createAuthenticatedClients(config)

  const tags = await withHttpErrors(config, { forbidden: COLLECTION_FORBIDDEN_MESSAGE }, async () => {
    const collectionId = await resolveTargetCollectionId(clients, config, options.collection)
    const { tagList } = await clients.tags.apiTagsGet({ collectionId })
    // Alphabetical: the server returns creation order, which is not a useful
    // way to read a tag list.
    return [...tagList].sort((a, b) => a.data.name.localeCompare(b.data.name))
  })

  switch (format) {
    case 'json':
      console.log(JSON.stringify(tags.map((tag) => ({ id: tag.id, name: tag.data.name })), null, 2))
      break
    case 'ids':
      for (const tag of tags) console.log(tag.id)
      break
    case 'table':
      console.log(renderTable(['ID', 'Name'], tags.map((tag) => [tag.id, tag.data.name])))
      break
  }
}

export interface TagsRenameOptions {
  collection?: string
}

/** `linkweave tags rename <tag> <new-name>` */
export async function runTagsRename(
  spec: string,
  newName: string,
  options: TagsRenameOptions,
  cmd: Command,
): Promise<void> {
  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)

  const previousName = await withHttpErrors(
    config,
    { forbidden: COLLECTION_FORBIDDEN_MESSAGE },
    async () => {
      const collectionId = await resolveTargetCollectionId(clients, config, options.collection)
      const tag = await findTag(clients.tags, collectionId, spec)
      await clients.tags.apiTagsTagIdPut({
        tagId: tag.id,
        // The save payload replaces the tag outright, so the colour chosen in
        // the web UI is read back and sent along rather than being dropped.
        tagSaveJson: { collectionId, name: newName, color: tag.data.color },
      })
      return tag.data.name
    },
  )
  console.log(`✓ Tag renamed: ${previousName} → ${newName}`)
}

export interface TagsRmOptions {
  collection?: string
  yes?: boolean
}

/** `linkweave tags rm <tag>` — deletes outright; tags have no trashbin. */
export async function runTagsRm(
  spec: string,
  options: TagsRmOptions,
  cmd: Command,
): Promise<void> {
  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)

  const tag = await withHttpErrors(
    config,
    { forbidden: COLLECTION_FORBIDDEN_MESSAGE },
    async () => {
      const collectionId = await resolveTargetCollectionId(clients, config, options.collection)
      return findTag(clients.tags, collectionId, spec)
    },
  )
  // Deleting a tag strips it from every bookmark that carried it, and unlike
  // `bookmarks rm` there is nothing to restore afterwards.
  await confirmIrreversible(
    `Permanently delete tag '${tag.data.name}' and remove it from every bookmark? This cannot be undone.`,
    options.yes === true,
  )
  await withHttpErrors(
    config,
    { notFound: `Tag not found: ${tag.id}`, forbidden: 'Access denied.' },
    () => clients.tags.apiTagsTagIdDelete({ tagId: tag.id }),
  )
  console.log(`✓ Tag deleted: ${tag.data.name}`)
}
