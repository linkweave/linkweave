import type { Command } from 'commander'

import { createAuthenticatedClients } from '../client'
import { parseFormat, renderTable } from '../output'
import { effectiveConfig, resolveTargetCollectionId, withHttpErrors } from './commandHelpers'
import { COLLECTION_FORBIDDEN_MESSAGE } from './commandHelpers'

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
