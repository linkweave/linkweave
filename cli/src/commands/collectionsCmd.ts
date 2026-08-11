import type { Command } from 'commander'

import { CollectionSummaryJsonToJSON } from '../api'
import { createAuthenticatedClients } from '../client'
import { updateStoredDefaultCollection } from '../config'
import { CliError } from '../errors'
import { parseFormat, renderTable } from '../output'
import { findCollection } from '../resolve'
import type { NoOptions } from './commandHelpers'
import {
  COLLECTION_FORBIDDEN_MESSAGE,
  confirmIrreversible,
  effectiveConfig,
  withHttpErrors,
} from './commandHelpers'

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

/** `linkweave collections create <name>` */
export async function runCollectionsCreate(
  name: string,
  _options: NoOptions,
  cmd: Command,
): Promise<void> {
  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)

  const created = await withHttpErrors(config, {}, () =>
    clients.collections.apiCollectionsPost({ collectionCreateJson: { name } }),
  )
  console.log(`✓ Collection created: ${created.name} (${created.id})`)
}

/** `linkweave collections rename <collection> <new-name>` */
export async function runCollectionsRename(
  spec: string,
  newName: string,
  _options: NoOptions,
  cmd: Command,
): Promise<void> {
  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)

  const renamed = await withHttpErrors(
    config,
    { forbidden: COLLECTION_FORBIDDEN_MESSAGE },
    async () => {
      const collection = await findCollection(clients.collections, spec)
      // Renaming is owner-only, and the server enforces that by keeping the old
      // name rather than refusing: an admin's rename comes back 200 as if it
      // had worked. The role on the listing is the actual reason, so it decides
      // this — inferring it from an unchanged name would also accuse an owner
      // who renamed a collection to the name it already had.
      if (collection.role !== 'OWNER') {
        throw new CliError(
          `Collection not renamed: renaming '${collection.name}' is restricted to its owner.`,
        )
      }
      // The update endpoint replaces the whole collection, and its payload
      // carries the screenshot toggle and fetch allowlist as well as the name.
      // Sending defaults for those would silently reset settings the CLI does
      // not even expose, so they are read back and passed through untouched.
      const existing = await clients.collections.apiCollectionsIdGet({ id: collection.id })
      const updated = await clients.collections.apiCollectionsIdPut({
        id: collection.id,
        collectionUpdateJson: {
          name: newName,
          browserFetchAllowlist: existing.browserFetchAllowlist,
          screenshotEnabled: existing.screenshotEnabled,
        },
      })
      return { before: collection.name, after: updated.name }
    },
  )

  // The stored name, not the requested one: the server trims and otherwise
  // normalises it, and reporting what we asked for would be a plain lie.
  console.log(`✓ Collection renamed: ${renamed.before} → ${renamed.after}`)
}

export interface CollectionRmOptions {
  yes?: boolean
}

/** `linkweave collections rm <collection>` — hard delete, unlike `bookmarks rm`. */
export async function runCollectionsRm(
  spec: string,
  options: CollectionRmOptions,
  cmd: Command,
): Promise<void> {
  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)

  const collection = await withHttpErrors(config, { forbidden: COLLECTION_FORBIDDEN_MESSAGE }, () =>
    findCollection(clients.collections, spec),
  )
  // Deleting is owner-only and the server does refuse — but with a 403 that
  // only arrives after the prompt below has made someone confirm destroying a
  // collection they were never able to destroy. The role settles it first; the
  // 403 mapping stays for access that changes in between.
  if (collection.role !== 'OWNER') {
    throw new CliError(
      `Collection not deleted: deleting '${collection.name}' is restricted to its owner.`,
    )
  }
  // Nothing about this reaches the trashbin: the bookmarks, folders, tags,
  // auto-tag rules and saved searches inside are all deleted outright.
  await confirmIrreversible(
    `Permanently delete collection '${collection.name}' and everything in it? This cannot be undone.`,
    options.yes === true,
  )
  await withHttpErrors(
    config,
    {
      forbidden: 'Access denied. Only the collection owner can delete it.',
      notFound: `Collection not found: ${collection.id}`,
    },
    () => clients.collections.apiCollectionsIdDelete({ id: collection.id }),
  )
  console.log(`✓ Collection deleted: ${collection.name}`)
}

/** `linkweave collections default <collection>` */
export async function runCollectionsSetDefault(
  spec: string,
  _options: NoOptions,
  cmd: Command,
): Promise<void> {
  const config = effectiveConfig(cmd)
  const clients = createAuthenticatedClients(config)

  const collection = await withHttpErrors(
    config,
    { forbidden: COLLECTION_FORBIDDEN_MESSAGE },
    async () => {
      const found = await findCollection(clients.collections, spec)
      await clients.collections.apiCollectionsIdDefaultPut({ id: found.id })
      return found
    },
  )
  // Commands prefer the default captured at login, so the stored copy has to
  // follow the server or this would look like a no-op from the next command on.
  updateStoredDefaultCollection(config, collection.id)
  console.log(`✓ Default collection: ${collection.name}`)
}
