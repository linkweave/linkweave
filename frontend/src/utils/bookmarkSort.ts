import type { BookmarkJson, SortDirection, SortField } from '@/api/generated'

export type SortPref = {
  field: SortField
  direction: SortDirection
}

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
export function sortBookmarks(list: readonly BookmarkJson[], pref: SortPref): SortedBookmarks {
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
