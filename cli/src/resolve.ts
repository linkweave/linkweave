import type {
  CollectionResourceApi,
  CollectionSummaryJson,
  FolderResourceApi,
  TagResourceApi,
} from './api'
import { CliError } from './errors'

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function looksLikeId(value: string): boolean {
  return UUID_PATTERN.test(value)
}

type CollectionsApi = Pick<CollectionResourceApi, 'apiCollectionsGet'>
type TagsApi = Pick<TagResourceApi, 'apiTagsGet' | 'apiTagsPost'>
type FoldersApi = Pick<FolderResourceApi, 'apiFoldersGet' | 'apiFoldersPost'>

/**
 * Resolves a `--collection` value to a collection ID (UC-079 A8): UUIDs pass
 * through, anything else is matched case-insensitively against the user's
 * collection names.
 */
export async function resolveCollectionId(
  collections: CollectionsApi,
  spec: string,
): Promise<string> {
  if (looksLikeId(spec)) return spec
  const { collections: all } = await collections.apiCollectionsGet()
  const needle = spec.toLowerCase()
  const matches = all.filter((c: CollectionSummaryJson) => c.name.toLowerCase() === needle)
  if (matches.length === 1) return matches[0]!.id
  if (matches.length > 1) {
    throw new CliError(`Multiple collections match '${spec}'. Use the collection ID instead.`)
  }
  throw new CliError(
    `No collection found with name '${spec}'. Use 'linkweave collections list' to see your collections.`,
  )
}

/** Splits a comma-separated `--tags` value into trimmed, de-duplicated names. */
export function parseTagNames(input: string): string[] {
  const seen = new Set<string>()
  const names: string[] = []
  for (const raw of input.split(',')) {
    const name = raw.trim()
    const key = name.toLowerCase()
    if (name && !seen.has(key)) {
      seen.add(key)
      names.push(name)
    }
  }
  return names
}

/**
 * Resolves tag names to IDs within a collection (BR-019). Names are matched
 * case-insensitively; unknown tags are auto-created.
 */
export async function resolveTagIds(
  tags: TagsApi,
  collectionId: string,
  names: string[],
): Promise<string[]> {
  if (names.length === 0) return []
  const { tagList } = await tags.apiTagsGet({ collectionId })
  const byName = new Map(tagList.map((tag) => [tag.data.name.toLowerCase(), tag.id]))
  const ids: string[] = []
  for (const name of names) {
    const existing = byName.get(name.toLowerCase())
    if (existing) {
      ids.push(existing)
    } else {
      const created = await tags.apiTagsPost({ tagSaveJson: { collectionId, name } })
      byName.set(name.toLowerCase(), created.id)
      ids.push(created.id)
    }
  }
  return ids
}

export interface ResolveFolderOptions {
  /** Auto-create missing path segments (BR-020, used by `bookmarks add`). */
  create: boolean
}

/**
 * Resolves a folder path like `Dev/TypeScript/Articles` to a folder ID by
 * walking the hierarchy segment by segment (BR-020). Matching is
 * case-insensitive. With `create: false` a missing segment is an error.
 */
export async function resolveFolderId(
  folders: FoldersApi,
  collectionId: string,
  path: string,
  options: ResolveFolderOptions,
): Promise<string> {
  const segments = path
    .split('/')
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
  if (segments.length === 0) throw new CliError(`Invalid folder path: '${path}'`)

  const { folderList } = await folders.apiFoldersGet({ collectionId })
  const active = folderList.filter((f) => f.deletedAt === undefined || f.deletedAt === null)

  let parentId: string | undefined = undefined
  for (const segment of segments) {
    const needle = segment.toLowerCase()
    const match = active.find(
      (f) => (f.data.parentId ?? undefined) === parentId && f.data.name.toLowerCase() === needle,
    )
    if (match) {
      parentId = match.id
      continue
    }
    if (!options.create) {
      throw new CliError(`No folder found at path '${path}' in the collection.`)
    }
    const created = await folders.apiFoldersPost({
      folderSaveJson: { collectionId, parentId, name: segment },
    })
    active.push(created)
    parentId = created.id
  }
  return parentId!
}
