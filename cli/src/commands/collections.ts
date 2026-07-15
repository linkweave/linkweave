import type { Command } from 'commander'

import { CollectionSummaryJsonToJSON } from '../api'
import { createAuthenticatedClients } from '../client'
import { parseFormat, renderTable } from '../output'
import { effectiveConfig, withHttpErrors } from './shared'

export interface CollectionListOptions {
  format?: string
}

/** `linkweave collections list` */
export async function runCollectionsList(
  options: CollectionListOptions,
  cmd: Command,
): Promise<void> {
  const config = effectiveConfig(cmd)
  const format = parseFormat(options.format ?? 'table')
  const clients = createAuthenticatedClients(config)

  const { collections } = await withHttpErrors(config, {}, () =>
    clients.collections.apiCollectionsGet(),
  )

  switch (format) {
    case 'json':
      console.log(JSON.stringify(collections.map(CollectionSummaryJsonToJSON), null, 2))
      break
    case 'ids':
      for (const collection of collections) console.log(collection.id)
      break
    case 'table':
      console.log(
        renderTable(
          ['ID', 'Name', 'Default', 'Role', 'Shared'],
          collections.map((c) => [
            c.id,
            c.name,
            c.isDefault ? 'yes' : '',
            String(c.role),
            c.shared ? 'yes' : '',
          ]),
        ),
      )
      break
  }
}
