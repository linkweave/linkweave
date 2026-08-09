import type {
  CollectionResourceApi,
  CollectionSummaryJson,
  FolderJson,
  FolderResourceApi,
  TagJson,
  TagResourceApi,
} from './api'
import { CliError } from './errors'

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function looksLikeId(value: string): boolean {
  return UUID_PATTERN.test(value)
}

type CollectionsApi = Pick<CollectionResourceApi, 'apiCollectionsGet'>
type TagsApi = Pick<TagResourceApi, 'apiTagsGet' | 'apiTagsPost'>
type TagLookupApi = Pick<TagResourceApi, 'apiTagsGet'>
type FoldersApi = Pick<FolderResourceApi, 'apiFoldersGet' | 'apiFoldersPost'>
type FolderLookupApi = Pick<FolderResourceApi, 'apiFoldersGet'>

/**
 * Resolves a `--collection` value to a collection ID (UC-079 A8): UUIDs pass
 * through, anything else is matched case-insensitively against the user's
 * collection names.
 */
export async function resolveCollectionId(
  collections: CollectionsApi,
  spec: string,
  // Shell completion passes an AbortSignal here: without it this lookup has no
  // deadline, and a hung server would keep the completion process alive.
  init?: RequestInit,
): Promise<string> {
  if (looksLikeId(spec)) return spec
  const { collections: all } = await collections.apiCollectionsGet(init)
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

/**
 * Like resolveCollectionId, but returns the collection itself.
 *
 * The management commands need more than the ID — the name for their
 * confirmation prompts and result messages — and an ID that only ever gets
 * echoed back is no help to someone deciding whether to delete something.
 */
export async function findCollection(
  collections: CollectionsApi,
  spec: string,
): Promise<CollectionSummaryJson> {
  const { collections: all } = await collections.apiCollectionsGet()
  const needle = spec.toLowerCase()
  const matches = looksLikeId(spec)
    ? all.filter((c) => c.id === spec)
    : all.filter((c) => c.name.toLowerCase() === needle)
  if (matches.length === 1) return matches[0]!
  if (matches.length > 1) {
    throw new CliError(`Multiple collections match '${spec}'. Use the collection ID instead.`)
  }
  throw new CliError(
    `No collection found matching '${spec}'. Use 'linkweave collections list' to see your collections.`,
  )
}

/**
 * Resolves a tag spec — an ID or a name — to the tag itself, without ever
 * creating one. `resolveTagIds` auto-creates because tagging a bookmark with a
 * new name is a normal thing to do; renaming or deleting a tag that does not
 * exist is not, so a miss here is an error.
 */
export async function findTag(
  tags: TagLookupApi,
  collectionId: string,
  spec: string,
): Promise<TagJson> {
  const { tagList } = await tags.apiTagsGet({ collectionId })
  const needle = spec.toLowerCase()
  const matches = looksLikeId(spec)
    ? tagList.filter((tag) => tag.id === spec)
    : tagList.filter((tag) => tag.data.name.toLowerCase() === needle)
  if (matches.length === 1) return matches[0]!
  if (matches.length > 1) {
    throw new CliError(`Multiple tags match '${spec}'. Use the tag ID instead.`)
  }
  throw new CliError(
    `No tag found matching '${spec}' in the collection. Use 'linkweave tags list' to see them.`,
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

/** Splits a folder path into its trimmed, non-empty segments. */
export function folderPathSegments(path: string): string[] {
  return path
    .split('/')
    .map((segment) => segment.trim())
    .filter((segment) => segment.length > 0)
}

/**
 * Canonical form of a user-typed folder path, so `Dev / TypeScript/` matches
 * the `Dev/TypeScript` that `folders list` prints.
 */
export function normalizeFolderPath(path: string): string {
  return folderPathSegments(path).join('/')
}

/**
 * Resolves a folder path — or a folder ID — to the folder and its full path.
 *
 * The management commands need the whole folder, not just its ID: renaming
 * sends a full FolderSaveJson back, and the server treats an absent parentId
 * as "move to the root", so the current parent has to be read first and passed
 * through unchanged. The path comes back too, so their messages can name the
 * folder the same way `folders list` does even when an ID was passed in.
 */
export async function findFolder(
  folders: FolderLookupApi,
  collectionId: string,
  path: string,
): Promise<FolderPath> {
  const { folderList } = await folders.apiFoldersGet({ collectionId })
  const known = folderPaths(folderList.filter(isLiveFolder))

  if (looksLikeId(path)) {
    const byId = known.find((entry) => entry.folder.id === path)
    if (byId) return byId
    throw new CliError(`No folder found with ID '${path}' in the collection.`)
  }

  const wanted = normalizeFolderPath(path)
  if (wanted === '') throw new CliError(`Invalid folder path: '${path}'`)
  const matches = known.filter((entry) => entry.path.toLowerCase() === wanted.toLowerCase())
  if (matches.length === 1) return matches[0]!
  if (matches.length > 1) {
    throw new CliError(`Multiple folders match '${path}'. Use the folder ID instead.`)
  }
  throw new CliError(
    `No folder found at path '${wanted}' in the collection. Use 'linkweave folders list' to see them.`,
  )
}

/** The path of a folder path's parent, or '' when it sits at the root. */
export function parentFolderPath(path: string): string {
  return folderPathSegments(path).slice(0, -1).join('/')
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
  const segments = folderPathSegments(path)
  if (segments.length === 0) throw new CliError(`Invalid folder path: '${path}'`)

  const { folderList } = await folders.apiFoldersGet({ collectionId })
  const active = folderList.filter(isLiveFolder)

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

/** A folder is soft-deleted until the trashbin is emptied or it is restored. */
export function isLiveFolder(folder: FolderJson): boolean {
  return folder.deletedAt === undefined || folder.deletedAt === null
}

export interface FolderPath {
  folder: FolderJson
  /** Full path, e.g. `Dev/TypeScript`, built from the folders passed in. */
  path: string
}

/**
 * Pairs each folder with its full path, so `folders list` and `--folder`
 * completion speak the same syntax the flag accepts.
 *
 * Returns pairs rather than a bare `string[]`, and filters nothing: a caller
 * that had to line a path array up against its own filtered copy of the input
 * would be one predicate change away from silently mismatching ids and paths.
 * Callers select what belongs in the result — `isLiveFolder` for the live
 * listings, the trashed set as-is for the trashbin — and the pairing cannot
 * drift from what they passed.
 */
export function folderPaths(folderList: FolderJson[]): FolderPath[] {
  const byId = new Map(folderList.map((folder) => [folder.id, folder]))
  return folderList.map((folder) => {
    const segments: string[] = []
    let current: FolderJson | undefined = folder
    // The guard is against a parent cycle in server data: walking one forever
    // would hang `folders list` and, worse, the user's shell during completion.
    const seen = new Set<string>()
    while (current !== undefined && !seen.has(current.id)) {
      seen.add(current.id)
      segments.unshift(current.data.name)
      const parentId: string | undefined = current.data.parentId
      current = parentId === undefined ? undefined : byId.get(parentId)
    }
    return { folder, path: segments.join('/') }
  })
}
