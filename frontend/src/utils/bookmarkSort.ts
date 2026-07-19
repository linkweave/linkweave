import type { BookmarkJson, SortDirection, SortField } from '@/api/generated'

export type SortPref = {
  field: SortField
  direction: SortDirection
}

/**
 * Context for the MANUAL sort mode (UC-103, BR-197): ranks each folder by its
 * position in a depth-first walk of the folder tree (parents before
 * descendants, siblings in manual order), so a list spanning several folders
 * groups by folder. Bookmarks whose folder is unknown — and unfiled ones —
 * sort last, matching the grouped layout's unfiled card.
 */
export type ManualOrderContext = {
  folderRank: Map<string, number>
}

const UNFILED_RANK = Number.MAX_SAFE_INTEGER

export const SYSTEM_DEFAULT_SORT: SortPref = {
  field: 'DATE_ADDED',
  direction: 'DESC',
}

function createdAtMs(b: BookmarkJson): number {
  const v = b.entityInfo?.timestampErstellt
  return v ? new Date(v).getTime() : 0
}

function lastClickedMs(b: BookmarkJson): number | null {
  return b.lastClickedAt ? new Date(b.lastClickedAt).getTime() : null
}

/**
 * Manual order within one folder group (UC-103): the shared sortOrder, ties
 * broken like the backend does it — older bookmark first, then id (BR-198).
 * Single source of truth for every frontend spot that ranks siblings
 * (sorting, undo snapshots, no-op drop detection).
 */
export function byManualBookmarkOrder(a: BookmarkJson, b: BookmarkJson): number {
  if (a.sortOrder !== b.sortOrder) return a.sortOrder - b.sortOrder
  const byCreation = createdAtMs(a) - createdAtMs(b)
  if (byCreation !== 0) return byCreation
  return a.id.localeCompare(b.id)
}

/**
 * MANUAL mode order (BR-197/BR-198): folder group first (DFS rank), then the
 * manual order within the group. Direction deliberately plays no part — a
 * manual order has only one reading.
 */
function compareManual(a: BookmarkJson, b: BookmarkJson, folderRank: Map<string, number>): number {
  const ra = a.data.folderId ? (folderRank.get(a.data.folderId) ?? UNFILED_RANK) : UNFILED_RANK
  const rb = b.data.folderId ? (folderRank.get(b.data.folderId) ?? UNFILED_RANK) : UNFILED_RANK
  if (ra !== rb) return ra - rb
  return byManualBookmarkOrder(a, b)
}

export function isNeverOpened(b: BookmarkJson, field: SortField): boolean {
  if (field === 'LAST_CLICKED') return b.lastClickedAt == null
  if (field === 'CLICK_COUNT') return (b.clickCount ?? 0) === 0
  return false
}

function compare(a: BookmarkJson, b: BookmarkJson, pref: SortPref): number {
  const mult = pref.direction === 'ASC' ? 1 : -1
  let c = 0
  switch (pref.field) {
    case 'TITLE':
      c = (a.data.title ?? '').localeCompare(b.data.title ?? '', undefined, { sensitivity: 'base' })
      break
    case 'DATE_ADDED':
      c = createdAtMs(a) - createdAtMs(b)
      break
    case 'LAST_CLICKED': {
      const al = lastClickedMs(a)
      const bl = lastClickedMs(b)
      // never-clicked is handled by isNeverOpened upstream; here both sides have values
      c = (al ?? 0) - (bl ?? 0)
      break
    }
    case 'CLICK_COUNT':
      c = (a.clickCount ?? 0) - (b.clickCount ?? 0)
      break
  }
  if (c === 0) {
    // Stable tie-breaker: newest first, independent of the requested direction.
    return createdAtMs(b) - createdAtMs(a)
  }
  return c * mult
}

export type SortedBookmarks = {
  items: BookmarkJson[]
  neverOpenedCount: number
}

/**
 * Returns the list sorted per `pref` plus the number of never-opened entries
 * at the tail. For click-based sorts (LAST_CLICKED / CLICK_COUNT), bookmarks
 * with no click activity are appended at the end, ordered by createdAt desc,
 * and `neverOpenedCount` reflects how many that is. For other sorts,
 * `neverOpenedCount` is 0.
 */
export function sortBookmarks(
  list: readonly BookmarkJson[],
  pref: SortPref,
  manual?: ManualOrderContext,
): SortedBookmarks {
  if (pref.field === 'MANUAL') {
    const folderRank = manual?.folderRank ?? new Map<string, number>()
    return {
      items: [...list].sort((a, b) => compareManual(a, b, folderRank)),
      neverOpenedCount: 0,
    }
  }
  const isClickBased = pref.field === 'LAST_CLICKED' || pref.field === 'CLICK_COUNT'
  if (!isClickBased) {
    return { items: [...list].sort((a, b) => compare(a, b, pref)), neverOpenedCount: 0 }
  }
  const primary: BookmarkJson[] = []
  const neverOpened: BookmarkJson[] = []
  for (const b of list) {
    if (isNeverOpened(b, pref.field)) neverOpened.push(b)
    else primary.push(b)
  }
  primary.sort((a, b) => compare(a, b, pref))
  neverOpened.sort((a, b) => createdAtMs(b) - createdAtMs(a))
  return { items: [...primary, ...neverOpened], neverOpenedCount: neverOpened.length }
}
