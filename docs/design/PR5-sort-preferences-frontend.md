# PR 5 — Sort Preferences: Frontend

**Goal.** Wire the backend sort preference (PR4) into the bookmark list and surface a Sort dropdown in the sticky toolbar (PR2).

**Depends on.** PR 2 (sticky toolbar with `<slot name="sort">`) and PR 4 (backend endpoints + DTOs).
**Scope.** Pure frontend. Click tracking already wired (`useBookmarkStore.trackClick`). Sort is applied client-side on top of the existing `filteredBookmarks` computed.

> **Scope note — per-user default sort dropped.** PR4 cut the user-level default sort. PR5 follows: no Settings-dialog section, no "Use as default" action, no hydration from `/auth/me`. Sort resolves to `per-collection preference ?? system default (DATE_ADDED, DESC)`.

> **Architectural choices made during implementation:**
>
> - **No new `useSortStore`** — the existing `useCollectionStore` already holds `settings` (which now includes `sortField`/`sortDirection`) and a debounced `updateSettings` via `useCollectionSettingsWriter`. We add `effectiveSort`, `hasSortOverride`, and `resetSortPreference` to that store. A parallel sort store would have duplicated debouncing, hydration, and offline behaviour for one extra concern.
> - **`filteredBookmarks` stays a flat array.** An earlier draft proposed splitting into `{ primary, neverOpened }`, but several consumers read `filteredBookmarks.length` and iterate the flat list. Instead, the sort util appends never-clicked items to the end and the bookmark store exposes `neverOpenedCount` so the view can render a divider at index `length - neverOpenedCount`.

---

## Files touched

| File | Change |
|---|---|
| `frontend/src/api/generated/**` | Regenerated post-PR4 — `SortField`, `SortDirection`, extended `CollectionSettingsJson`, `apiCollectionsIdSettingsSortDelete`. |
| `frontend/src/utils/bookmarkSort.ts` | **New.** Pure comparator + grouper + helpers. Unit-tested. |
| `frontend/src/utils/bookmarkSort.spec.ts` | **New.** Unit tests. |
| `frontend/src/stores/collection.ts` | Adds `effectiveSort`, `hasSortOverride`, `resetSortPreference`. |
| `frontend/src/stores/bookmark.ts` | `filteredBookmarks` now applies `sortBookmarks` after filtering; exposes `neverOpenedCount`. |
| `frontend/src/components/bookmark/BookmarkSortMenu.vue` | **New.** radix-vue dropdown. |
| `frontend/src/components/bookmark/NeverOpenedDivider.vue` | **New.** Inline divider with label + count. |
| `frontend/src/components/bookmark/BookmarkList.vue` | Renders `NeverOpenedDivider` at the right index in list + grid layouts. |
| `frontend/src/components/bookmark/index.ts` | Exports the two new components. |
| `frontend/src/views/CollectionView.vue` | Passes `<BookmarkSortMenu>` into the toolbar's `#sort` slot. |
| `frontend/src/i18n/locales/{en,de}.json` | New `sort.*` strings. |
| `frontend/e2e/sort-preferences.spec.ts` | **New.** Playwright spec. |

---

## Data model — frontend types

```ts
// frontend/src/utils/bookmarkSort.ts
import type { BookmarkJson, SortDirection, SortField } from '@/api/generated'

export type SortPref = {
  field: SortField        // 'TITLE' | 'DATE_ADDED' | 'LAST_CLICKED' | 'CLICK_COUNT'
  direction: SortDirection // 'ASC' | 'DESC'
}

export const SYSTEM_DEFAULT_SORT: SortPref = {
  field: 'DATE_ADDED',
  direction: 'DESC',
}
```

---

## `bookmarkSort.ts` — pure logic, unit-testable

Important field paths in `BookmarkJson`:

- `bookmark.data.title` — title.
- `bookmark.entityInfo.timestampErstellt` — creation timestamp.
- `bookmark.lastClickedAt` / `bookmark.clickCount` — **top-level**, not under `data`.

```ts
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
      // never-clicked is filtered out upstream; both sides have values here
      c = (al ?? 0) - (bl ?? 0)
      break
    }
    case 'CLICK_COUNT':
      c = (a.clickCount ?? 0) - (b.clickCount ?? 0)
      break
  }
  if (c === 0) {
    // Stable tie-breaker: newest first, independent of requested direction.
    return createdAtMs(b) - createdAtMs(a)
  }
  return c * mult
}

/**
 * Flat sorted array. For click-based sorts, bookmarks with no click activity
 * are appended at the end ordered by createdAt desc.
 */
export function sortBookmarks(list: readonly BookmarkJson[], pref: SortPref): BookmarkJson[] {
  const isClickBased = pref.field === 'LAST_CLICKED' || pref.field === 'CLICK_COUNT'
  if (!isClickBased) return [...list].sort((a, b) => compare(a, b, pref))

  const primary: BookmarkJson[] = []
  const neverOpened: BookmarkJson[] = []
  for (const b of list) {
    if (isNeverOpened(b, pref.field)) neverOpened.push(b)
    else primary.push(b)
  }
  primary.sort((a, b) => compare(a, b, pref))
  neverOpened.sort((a, b) => createdAtMs(b) - createdAtMs(a))
  return [...primary, ...neverOpened]
}

export function countNeverOpened(list: readonly BookmarkJson[], field: SortField): number {
  if (field !== 'LAST_CLICKED' && field !== 'CLICK_COUNT') return 0
  let n = 0
  for (const b of list) if (isNeverOpened(b, field)) n++
  return n
}
```

**Unit tests** under `frontend/src/utils/bookmarkSort.spec.ts`:

- titles A→Z case-insensitively
- DATE_ADDED newest first by default (DESC)
- stable tie-break on createdAt desc when sort values match
- LAST_CLICKED sinks never-clicked to end
- CLICK_COUNT sinks zero-click to end
- never-opened group is createdAt-desc regardless of direction
- `countNeverOpened` returns 0 for non-click sorts, correct count for click sorts

---

## `useCollectionStore` — extension (no new store)

```diff
+import {SYSTEM_DEFAULT_SORT, type SortPref} from '@/utils/bookmarkSort'

   const settingsLayout = computed<'list' | 'grid' | 'grouped' | null>(() => {
     const v = settings.value?.layout
     return v === 'list' || v === 'grid' || v === 'grouped' ? v : null
   })

+  const effectiveSort = computed<SortPref>(() => ({
+    field: settings.value?.sortField ?? SYSTEM_DEFAULT_SORT.field,
+    direction: settings.value?.sortDirection ?? SYSTEM_DEFAULT_SORT.direction,
+  }))
+
+  const hasSortOverride = computed(
+    () => settings.value?.sortField != null || settings.value?.sortDirection != null,
+  )
+
+  async function resetSortPreference(collectionId: string) {
+    try {
+      await collectionApi.apiCollectionsIdSettingsSortDelete({ id: collectionId })
+      if (settings.value) {
+        settings.value = { ...settings.value, sortField: undefined, sortDirection: undefined }
+      }
+    } catch (err) {
+      console.error('Failed to reset sort preference:', err)
+      useNotificationStore().handleApiError(err, 'Failed to reset sort preference')
+    }
+  }
```

Writes go through the existing `updateSettings(collectionId, patch)` — the debounced writer (`useCollectionSettingsWriter`) handles the optimistic UI + debounced PUT, and merges `{ sortField, sortDirection }` into `settings`.

> **Note: no explicit "hydrate sort" call.** `fetchCollectionInfo()` already fetches `GET /collections/{id}/settings` in parallel with the collection info — sort fields ride along.

---

## `useBookmarkStore` — apply sort

```diff
+import { countNeverOpened, sortBookmarks } from '@/utils/bookmarkSort'

   const filteredBookmarks = computed(() => {
     const folderStore = useFolderStore()
     const tagStore = useTagStore()
     let result = bookmarks.value
     // ... folder, tag, search filters unchanged ...
-    return result
+    return sortBookmarks(result, collectionStore.effectiveSort)
   })
+
+  const neverOpenedCount = computed(() =>
+    countNeverOpened(filteredBookmarks.value, collectionStore.effectiveSort.field),
+  )
```

> **Search + sort.** When the search query is active, we still apply the user's sort. We have no relevance score yet; revisit when FR-071 (search operators) lands.

---

## `BookmarkSortMenu.vue`

Built on `radix-vue` (the same primitives `UserMenuCl.vue` uses — no shadcn-vue dependency).

Trigger button: `data-testid="bookmark-sort-trigger"`, shows `<label>: <field> <↑/↓>` and a chevron.

Menu structure:
- Header row with the menu title; if `hasSortOverride`, a "Reset" link (`data-testid="bookmark-sort-reset"`) on the right.
- One `DropdownMenuItem` per field (`data-testid="bookmark-sort-option-{TITLE|DATE_ADDED|LAST_CLICKED|CLICK_COUNT}"`). On the active row, an inline ASC/DESC toggle is rendered to flip direction without closing the menu (`e.preventDefault(); e.stopPropagation();` on click + `@pointerdown.stop`).
- When the active field is `LAST_CLICKED` or `CLICK_COUNT`, a small info footer appears: *"Last clicked" and "Click count" reflect everyone in this collection.*

Field picks call `collectionStore.updateSettings(id, { sortField, sortDirection })` — passing both fields keeps direction stable across field switches and lets the debounced writer coalesce them into one PUT.

Reset calls `collectionStore.resetSortPreference(id)` (DELETE endpoint).

### Wire into the toolbar

```diff
- <BookmarkListToolbar />
+ <BookmarkListToolbar>
+   <template #sort>
+     <BookmarkSortMenu />
+   </template>
+ </BookmarkListToolbar>
```

---

## `NeverOpenedDivider.vue` and `BookmarkList.vue` integration

`NeverOpenedDivider` is a small dashed-pill divider with the count, marked `data-testid="never-opened-divider"`. The `col-span-full` class lets it span the full row inside the grid layout.

`BookmarkList.vue` computes the divider index:

```ts
const neverOpenedDividerAt = computed(() => {
  const n = bookmarkStore.neverOpenedCount
  if (n === 0) return -1
  return bookmarkStore.filteredBookmarks.length - n
})
```

…and renders it inline inside the list + grid layouts using `<template v-for="(bookmark, idx) ...">`.

> **Grouped layout intentionally skipped.** That layout already imposes its own ordering by tag/folder; the within-group order still respects the sort field (the grouped layout consumes the same `filteredBookmarks`). Adding the divider inside groups was deferred.

---

## Locale additions

The `Sub` keys use the i18n key built as `sort.field.${field.toLowerCase()}Sub`, so `DATE_ADDED` → `sort.field.date_addedSub` etc.

```json
"sort": {
  "label": "Sort",
  "neverOpened": "Never opened · {n}",
  "menu": {
    "title": "Sort · this collection",
    "reset": "Reset",
    "sharedClicksNote": "\"Last clicked\" and \"Click count\" reflect everyone in this collection."
  },
  "field": {
    "title": "Title",
    "titleSub": "A → Z by title",
    "dateAdded": "Date added",
    "date_addedSub": "When you saved it",
    "lastClicked": "Last clicked",
    "last_clickedSub": "Most recently opened",
    "clickCount": "Click count",
    "click_countSub": "How often it gets visited"
  },
  "dir": {
    "az": "A → Z",
    "za": "Z → A",
    "oldestFirst": "Oldest first",
    "newestFirst": "Newest first",
    "mostRecent": "Most recent first",
    "leastVisited": "Least visited first",
    "mostVisited": "Most visited first"
  }
}
```

German parallels live in `de.json` with matching key paths.

---

## Playwright E2E (`sort-preferences.spec.ts`)

Uses `registerAndCaptureStorageState` (the standard e2e fixture) to seed a fresh user with a default collection, then API-seeds three bookmarks (Apple, Cherry, Banana — created in that order). Stable selectors: `data-testid="bookmark-sort-trigger"`, `data-testid="bookmark-sort-option-{FIELD}"`, `data-testid="bookmark-sort-reset"`.

Specs:

- `default sort is newest first` — fresh collection shows `Banana → Cherry → Apple`.
- `selecting Title A→Z reorders bookmarks alphabetically and persists` — picks TITLE, flips to ASC via the inline direction toggle, waits for the debounced PUT, reloads, re-asserts.
- `reset clears the per-collection override and falls back to newest first` — opens menu, clicks Reset, waits for DELETE, reloads, re-asserts default order.

The helper `visibleTitles(page)` waits for the first card's `h3` to be visible before reading, to avoid racing the SPA bootstrap on freshly-loaded pages.

---

## Acceptance checklist

- [x] `npm run generate-api` produces `SortField`, `SortDirection`, and the extended `CollectionSettingsJson`.
- [x] `effectiveSort` / `hasSortOverride` / `resetSortPreference` live on `useCollectionStore`.
- [x] `useBookmarkStore.filteredBookmarks` applies the sort after filtering; `neverOpenedCount` is exposed.
- [x] `BookmarkSortMenu` renders in the sticky toolbar with current field + direction visible on the trigger button.
- [x] Picking a field updates the list immediately (optimistic) and persists to backend (debounced PUT).
- [x] Direction toggle inside the active row flips ASC/DESC without closing the menu.
- [x] "Reset" only appears when a per-collection override exists; clicking it calls DELETE and falls back to newest-first.
- [x] Last-clicked / click-count sorts group never-opened bookmarks under a clear divider in list + grid layouts.
- [x] Stable tie-breaker on `createdAt desc`.
- [x] Shared click data is mentioned in the sort menu when LAST_CLICKED / CLICK_COUNT is selected.
- [x] All Playwright specs in `sort-preferences.spec.ts` pass; `npm run type-check` clean.
- [x] No regression in existing search / filter behaviour — sort runs after the existing filter chain.

---

## Out of scope

- Per-user default sort. Cut from PR4; reconsider if user feedback shows the per-collection-only model is painful.
- Search relevance sort. We have no relevance score yet; revisit when FR-071 (search operators) lands.
- Per-user click tracking. Aggregate behaviour is documented for users inside the sort menu.
- "Never opened" divider inside the `grouped` layout — within-group ordering already respects the sort, but the divider is currently rendered only in list + grid.
- Server-side sorting / pagination. Frontend remains the source of truth for ordering.
