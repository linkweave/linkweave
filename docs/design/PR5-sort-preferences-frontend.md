# PR 5 — Sort Preferences: Frontend

**Goal.** Wire the backend sort preference (PR4) into the bookmark list, surface a Sort dropdown in the sticky toolbar (PR2), and expose the user default in the Settings dialog.

**Depends on.** PR 2 (sticky toolbar with `<slot name="sort">`) and PR 4 (backend endpoints + DTOs).
**Scope.** Pure frontend. Click tracking already wired (`useBookmarkStore.trackClick`). Sort is applied client-side on top of the existing `filteredBookmarks` computed.

---

## Files touched

| File | Change |
|---|---|
| `frontend/src/api/generated/**` | Regenerate after PR4 lands — `npm run generate-api`. |
| `frontend/src/stores/sort.ts` | **New.** Pinia store: hydrate, resolve, persist sort preference. |
| `frontend/src/stores/bookmark.ts` | Extend `filteredBookmarks` to sort using `useSortStore`. Add a "never clicked" split for click-based sorts. |
| `frontend/src/stores/auth.ts` | Read `defaultSortField` / `defaultSortDirection` off `/auth/me` payload into the sort store. |
| `frontend/src/stores/collection.ts` | When the active collection loads, ensure its settings (including sort) are hydrated into the sort store. |
| `frontend/src/components/bookmark/BookmarkSortMenu.vue` | **New.** The dropdown — title/date/last-clicked/click-count + direction toggle + reset + "use as default". |
| `frontend/src/components/bookmark/BookmarkList.vue` | Render the `<NeverOpenedDivider>` between the primary and never-opened groups when applicable. |
| `frontend/src/components/bookmark/NeverOpenedDivider.vue` | **New.** Small inline divider with label + count. |
| `frontend/src/views/CollectionView.vue` | Pass `<BookmarkSortMenu>` into the toolbar's `#sort` slot. |
| `frontend/src/components/settings/SettingsDialog.vue` (or wherever your Settings UI lives) | Add a "Bookmarks" section: default sort field + direction. |
| `frontend/src/locales/*` | New i18n strings (listed at end). |
| `frontend/src/utils/bookmarkSort.ts` | **New.** Pure comparator + grouper. Unit-tested. |
| `frontend/e2e/sort-preferences.spec.ts` | **New.** Playwright spec. |

---

## Data model — frontend types

```ts
// frontend/src/types/sort.ts
import type { SortField, SortDirection } from '@/api/generated' // generated from PR4 enums

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

## `useSortStore` — full spec

```ts
// frontend/src/stores/sort.ts
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { SortField, SortDirection } from '@/api/generated'
import { CollectionResourceApi, UserSettingsResourceApi } from '@/api/generated'
import { config } from '@/api'
import { useCollectionStore } from '@/stores/collection'
import type { SortPref } from '@/types/sort'
import { SYSTEM_DEFAULT_SORT } from '@/types/sort'

const collectionApi = new CollectionResourceApi(config)
const userSettingsApi = new UserSettingsResourceApi(config)

export const useSortStore = defineStore('sort', () => {
  // Per-collection overrides keyed by collectionId. null = "no override, fall through".
  const perCollection = ref<Map<string, Partial<SortPref>>>(new Map())
  // User default. null = "no default set, use system default".
  const userDefault = ref<Partial<SortPref> | null>(null)
  // Current collection id (for the computed effective sort).
  const collectionStore = useCollectionStore()

  // Resolution: per-collection ?? user-default ?? system-default
  const effective = computed<SortPref>(() => {
    const cid = collectionStore.currentCollectionId
    const c = cid ? perCollection.value.get(cid) : undefined
    return {
      field: c?.field ?? userDefault.value?.field ?? SYSTEM_DEFAULT_SORT.field,
      direction: c?.direction ?? userDefault.value?.direction ?? SYSTEM_DEFAULT_SORT.direction,
    }
  })

  /** Hydrate user-default from /auth/me response (called from auth store on login). */
  function hydrateUserDefault(field?: SortField | null, direction?: SortDirection | null) {
    userDefault.value = (field || direction) ? { field: field ?? undefined, direction: direction ?? undefined } : null
  }

  /** Hydrate per-collection from GET /collections/{id}/settings (called on collection load). */
  async function hydrateCollection(collectionId: string) {
    const s = await collectionApi.apiCollectionsIdSettingsGet({ id: collectionId })
    const pref: Partial<SortPref> = {}
    if (s.sortField) pref.field = s.sortField
    if (s.sortDirection) pref.direction = s.sortDirection
    perCollection.value.set(collectionId, pref)
  }

  /** Update sort for the current collection. Optimistic + persisted. */
  async function setForCurrentCollection(patch: Partial<SortPref>) {
    const cid = collectionStore.currentCollectionId
    if (!cid) return
    const current = perCollection.value.get(cid) ?? {}
    const next = { ...current, ...patch }
    perCollection.value.set(cid, next)
    // Persist. Partial PUT — backend merges with existing layout pref.
    await collectionApi.apiCollectionsIdSettingsPut({
      id: cid,
      collectionSettingsJson: { sortField: next.field, sortDirection: next.direction },
    })
  }

  /** Reset per-collection override → falls back to user default. */
  async function resetForCurrentCollection() {
    const cid = collectionStore.currentCollectionId
    if (!cid) return
    perCollection.value.set(cid, {})
    await collectionApi.apiCollectionsIdSettingsSortDelete({ id: cid })
  }

  /** Set user default. Used by Settings dialog. */
  async function setUserDefault(patch: Partial<SortPref>) {
    const next = { ...(userDefault.value ?? {}), ...patch }
    userDefault.value = next
    await userSettingsApi.apiUsersMeSettingsPut({
      userSettingsJson: { defaultSortField: next.field, defaultSortDirection: next.direction },
    })
  }

  /** Pin current effective sort as the user default. Convenient action from the sort menu. */
  async function pinAsUserDefault() {
    await setUserDefault({ field: effective.value.field, direction: effective.value.direction })
  }

  /** Detect whether the current collection has its own override. */
  const hasCollectionOverride = computed(() => {
    const cid = collectionStore.currentCollectionId
    const c = cid ? perCollection.value.get(cid) : undefined
    return !!(c && (c.field || c.direction))
  })

  return {
    effective,
    userDefault,
    hasCollectionOverride,
    hydrateUserDefault,
    hydrateCollection,
    setForCurrentCollection,
    resetForCurrentCollection,
    setUserDefault,
    pinAsUserDefault,
  }
})
```

### Hydration wiring

- **`auth.ts`** — after `apiAuthMeGet()` resolves, call `useSortStore().hydrateUserDefault(me.defaultSortField, me.defaultSortDirection)`.
- **`collection.ts`** — after `apiCollectionsIdGet({ id })` resolves and a collection is selected, call `useSortStore().hydrateCollection(id)`. Cheap, one extra request; can be in parallel with the existing collection-info load.

---

## `bookmarkSort.ts` — pure logic, unit-testable

```ts
// frontend/src/utils/bookmarkSort.ts
import type { BookmarkJson } from '@/api/generated'
import type { SortPref } from '@/types/sort'

export type SortedGroups = {
  primary: BookmarkJson[]
  neverOpened: BookmarkJson[] // empty unless field is LAST_CLICKED or CLICK_COUNT
}

function getCreatedAt(b: BookmarkJson): string {
  // The audit timestampErstellt is on every entity. Adjust to the actual field name on the JSON.
  return b.data.timestampErstellt ?? ''
}

function compare(a: BookmarkJson, b: BookmarkJson, pref: SortPref): number {
  const mult = pref.direction === 'ASC' ? 1 : -1
  let c = 0
  switch (pref.field) {
    case 'TITLE':
      c = (a.data.title ?? '').localeCompare(b.data.title ?? '', undefined, { sensitivity: 'base' })
      break
    case 'DATE_ADDED':
      c = getCreatedAt(a).localeCompare(getCreatedAt(b))
      break
    case 'LAST_CLICKED': {
      const al = a.data.lastClickedAt
      const bl = b.data.lastClickedAt
      if (!al && !bl) c = 0
      else if (!al) return 1   // never-clicked always sinks
      else if (!bl) return -1
      else c = al.localeCompare(bl)
      break
    }
    case 'CLICK_COUNT': {
      const ac = a.data.clickCount ?? 0
      const bc = b.data.clickCount ?? 0
      if (ac === 0 && bc === 0) c = 0
      else if (ac === 0) return 1
      else if (bc === 0) return -1
      else c = ac - bc
      break
    }
  }
  if (c === 0) {
    // Stable tie-breaker: newest first.
    c = getCreatedAt(b).localeCompare(getCreatedAt(a))
  }
  return c * mult
}

export function sortBookmarks(list: BookmarkJson[], pref: SortPref): SortedGroups {
  const isClickBased = pref.field === 'LAST_CLICKED' || pref.field === 'CLICK_COUNT'
  if (!isClickBased) {
    return { primary: [...list].sort((a, b) => compare(a, b, pref)), neverOpened: [] }
  }
  const hasActivity = (b: BookmarkJson) =>
    pref.field === 'LAST_CLICKED' ? !!b.data.lastClickedAt : (b.data.clickCount ?? 0) > 0
  const primary = list.filter(hasActivity).sort((a, b) => compare(a, b, pref))
  const neverOpened = list
    .filter((b) => !hasActivity(b))
    .sort((a, b) => getCreatedAt(b).localeCompare(getCreatedAt(a)))
  return { primary, neverOpened }
}
```

**Unit tests** under `frontend/src/utils/__tests__/bookmarkSort.spec.ts`:

- `sorts titles A→Z case-insensitively`
- `sorts dateAdded with newest-first by default`
- `tie-breaks on createdAt desc`
- `sinks never-clicked to neverOpened group when sorting by last clicked`
- `sinks zero-click bookmarks to neverOpened when sorting by click count`
- `neverOpened group always sorts by createdAt desc regardless of direction`

---

## `bookmark.ts` store — apply sort + relevance edge case

```diff
+import { useSortStore } from '@/stores/sort'
+import { sortBookmarks } from '@/utils/bookmarkSort'

   const filteredBookmarks = computed(() => {
     const folderStore = useFolderStore()
     const tagStore = useTagStore()
+    const sortStore = useSortStore()
     let result = bookmarks.value

     if (folderStore.selectedFolderId !== null) {
       result = result.filter(...)
     }
     if (tagStore.selectedTagIds.size > 0) {
       result = result.filter(...)
     }
     if (searchQuery.value.length >= 2) {
       ...
+      // When search is active, current product decision: keep the user's sort preference.
+      // (We have no relevance scoring yet; revisit when FR-071 / search operators land.)
     }

-    return result
+    // Sort happens AFTER filtering. We expose two arrays so the view can render
+    // a "Never opened" divider for click-based sorts.
+    return sortBookmarks(result, sortStore.effective)
   })
```

`filteredBookmarks` now returns `{ primary, neverOpened }`. Update consumers:

- `BookmarkList.vue` iterates `primary`, then renders `<NeverOpenedDivider :count="neverOpened.length" />` and iterates `neverOpened` when its length > 0.
- Anywhere else that reads `filteredBookmarks` (search active chip's count, etc) should sum `primary.length + neverOpened.length`.

If touching every consumer is a problem, expose two computed properties side-by-side:

```ts
const filteredBookmarks = computed(() => /* combined flat list as today */)
const filteredBookmarkGroups = computed(() => sortBookmarks(filteredBookmarks.value, sortStore.effective))
```

The combined flat list ordering should be `[...primary, ...neverOpened]` — so any consumer that doesn't care about grouping still gets a sensibly-ordered list with never-clicked at the end.

---

## `BookmarkSortMenu.vue` — full component

Use shadcn/vue's `DropdownMenu` for the popover so behaviour, keyboard nav, and focus management are free.

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowDown, ArrowUp, Check, ChevronDown, Pin, RotateCcw } from 'lucide-vue-next'
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuItem,
} from '@/components/ui/dropdown-menu' // shadcn/vue path may differ
import { ButtonCl } from '@/components/ui'
import { useSortStore } from '@/stores/sort'
import type { SortField } from '@/api/generated'

const { t } = useI18n()
const sortStore = useSortStore()

const FIELDS: { id: SortField; label: string; ascLabel: string; descLabel: string }[] = [
  { id: 'TITLE',        label: 'sort.field.title',       ascLabel: 'sort.dir.az',         descLabel: 'sort.dir.za' },
  { id: 'DATE_ADDED',   label: 'sort.field.dateAdded',   ascLabel: 'sort.dir.oldestFirst', descLabel: 'sort.dir.newestFirst' },
  { id: 'LAST_CLICKED', label: 'sort.field.lastClicked', ascLabel: 'sort.dir.oldestFirst', descLabel: 'sort.dir.mostRecent' },
  { id: 'CLICK_COUNT',  label: 'sort.field.clickCount',  ascLabel: 'sort.dir.leastVisited', descLabel: 'sort.dir.mostVisited' },
]

const currentField = computed(() => sortStore.effective.field)
const currentDir = computed(() => sortStore.effective.direction)
const currentFieldLabel = computed(() => t(FIELDS.find(f => f.id === currentField.value)!.label))

function pick(field: SortField) {
  sortStore.setForCurrentCollection({ field })
}
function flipDir(e: Event) {
  e.preventDefault() // keep menu open
  sortStore.setForCurrentCollection({ direction: currentDir.value === 'ASC' ? 'DESC' : 'ASC' })
}
function reset() { sortStore.resetForCurrentCollection() }
function pinDefault() { sortStore.pinAsUserDefault() }
</script>

<template>
  <DropdownMenu>
    <DropdownMenuTrigger as-child>
      <ButtonCl variant="ghost" size="sm" class="h-7 gap-1.5 text-xs">
        <span class="text-muted-foreground">{{ t('sort.label') }}:</span>
        <span class="font-medium">{{ currentFieldLabel }}</span>
        <component :is="currentDir === 'DESC' ? ArrowDown : ArrowUp" class="h-3 w-3 text-muted-foreground" />
        <ChevronDown class="h-3 w-3 text-muted-foreground" />
      </ButtonCl>
    </DropdownMenuTrigger>

    <DropdownMenuContent align="end" class="w-72">
      <DropdownMenuLabel class="flex items-center justify-between">
        <span>{{ t('sort.menu.title') }}</span>
        <button
          v-if="sortStore.hasCollectionOverride"
          type="button"
          class="text-xs text-muted-foreground hover:text-foreground inline-flex items-center gap-1"
          @click="reset"
        >
          <RotateCcw class="h-3 w-3" />
          {{ t('sort.menu.reset') }}
        </button>
      </DropdownMenuLabel>
      <DropdownMenuSeparator />

      <DropdownMenuItem
        v-for="f in FIELDS"
        :key="f.id"
        :class="['gap-2 cursor-pointer', f.id === currentField ? 'bg-accent' : '']"
        @click.prevent="pick(f.id)"
      >
        <Check class="h-3.5 w-3.5" :class="f.id === currentField ? '' : 'invisible'" />
        <div class="flex-1">
          <div class="text-sm font-medium">{{ t(f.label) }}</div>
          <div class="text-xs text-muted-foreground">
            {{ f.id === currentField ? t(currentDir === 'ASC' ? f.ascLabel : f.descLabel) : t('sort.field.' + f.id.toLowerCase() + 'Sub') }}
          </div>
        </div>
        <!-- direction toggle (only on the selected row) -->
        <div v-if="f.id === currentField" class="flex border border-border rounded overflow-hidden" @click.stop>
          <button
            type="button"
            :class="['px-1.5 py-0.5', currentDir === 'ASC' ? 'bg-accent text-foreground' : 'text-muted-foreground']"
            :aria-pressed="currentDir === 'ASC'"
            :aria-label="t(f.ascLabel)"
            @click="flipDir"
          >
            <ArrowUp class="h-3 w-3" />
          </button>
          <button
            type="button"
            :class="['px-1.5 py-0.5', currentDir === 'DESC' ? 'bg-accent text-foreground' : 'text-muted-foreground']"
            :aria-pressed="currentDir === 'DESC'"
            :aria-label="t(f.descLabel)"
            @click="flipDir"
          >
            <ArrowDown class="h-3 w-3" />
          </button>
        </div>
      </DropdownMenuItem>

      <DropdownMenuSeparator />
      <DropdownMenuItem class="text-xs text-muted-foreground gap-2" @click.prevent="pinDefault">
        <Pin class="h-3 w-3" />
        {{ t('sort.menu.useAsDefault') }}
      </DropdownMenuItem>
    </DropdownMenuContent>
  </DropdownMenu>
</template>
```

### Wire into the toolbar

In `CollectionView.vue`:

```diff
- <BookmarkListToolbar />
+ <BookmarkListToolbar>
+   <template #sort>
+     <BookmarkSortMenu />
+   </template>
+ </BookmarkListToolbar>
```

---

## `NeverOpenedDivider.vue` — full component

```vue
<script setup lang="ts">
import { Clock } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
defineProps<{ count: number }>()
const { t } = useI18n()
</script>

<template>
  <div class="flex items-center gap-3 my-4 col-span-full text-xs text-muted-foreground" role="separator">
    <div class="flex-1 h-px bg-border" />
    <div class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full border border-dashed border-border bg-card">
      <Clock class="h-3 w-3" />
      <span>{{ t('sort.neverOpened', { n: count }) }}</span>
    </div>
    <div class="flex-1 h-px bg-border" />
  </div>
</template>
```

Render in `BookmarkList.vue` between the two groups for the `grid`, `list`, and `grouped` layouts. (The divider's `col-span-full` makes it work cleanly inside the grid.) Hide in `grouped` mode unless the never-opened items would otherwise be lost — your call; the simplest is to render it identically across layouts.

---

## Settings dialog — "Bookmarks" section

Locate or create the settings dialog (project may not have one yet; if not, this PR depends on / creates a minimal version). Add a section:

```vue
<section class="space-y-2">
  <h3 class="text-sm font-semibold">{{ t('settings.bookmarks.defaultSort') }}</h3>
  <p class="text-xs text-muted-foreground">{{ t('settings.bookmarks.defaultSortHelp') }}</p>

  <div class="flex gap-2">
    <Select
      :model-value="userDefaultField"
      @update:model-value="(v) => sortStore.setUserDefault({ field: v })"
    >
      <SelectTrigger class="flex-1"><SelectValue /></SelectTrigger>
      <SelectContent>
        <SelectItem v-for="f in FIELDS" :key="f.id" :value="f.id">{{ t(f.label) }}</SelectItem>
      </SelectContent>
    </Select>

    <div class="flex border border-border rounded-md overflow-hidden">
      <button
        type="button"
        :class="['px-3', userDefaultDir === 'ASC' ? 'bg-accent text-foreground' : 'text-muted-foreground']"
        @click="sortStore.setUserDefault({ direction: 'ASC' })"
      >↑</button>
      <button
        type="button"
        :class="['px-3', userDefaultDir === 'DESC' ? 'bg-accent text-foreground' : 'text-muted-foreground']"
        @click="sortStore.setUserDefault({ direction: 'DESC' })"
      >↓</button>
    </div>
  </div>

  <p v-if="userDefault" class="text-xs">
    <button class="text-muted-foreground hover:text-foreground underline" @click="resetUserDefault">
      {{ t('settings.bookmarks.resetDefault') }}
    </button>
  </p>
</section>
```

Where `userDefaultField = computed(() => sortStore.userDefault?.field ?? 'DATE_ADDED')` etc.

`resetUserDefault` calls `userSettingsApi.apiUsersMeSettingsSortDelete()` and sets `sortStore.userDefault = null`.

A second sub-section for **click-tracking transparency**: a short note explaining that "Last clicked" and "Click count" use the data shared by all collection members. This pre-empts confusion in shared collections. No toggle — there's nothing to toggle since click data is already on the server.

```
"settings.bookmarks.clickTrackingNote":
"\"Last clicked\" and \"Click count\" use shared click data across all members of a collection. Your activity contributes to the same counts everyone else sees."
```

---

## Locale additions (en + de minimums)

```
sort.label: "Sort"
sort.menu.title: "Sort · this collection"
sort.menu.reset: "Reset"
sort.menu.useAsDefault: "Use as default for new collections"
sort.field.title: "Title"
sort.field.titleSub: "A → Z by title"
sort.field.dateAdded: "Date added"
sort.field.dateAddedSub: "When you saved it"
sort.field.lastClicked: "Last clicked"
sort.field.lastClickedSub: "Most recently opened"
sort.field.clickCount: "Click count"
sort.field.clickCountSub: "How often you visit it"
sort.dir.az: "A → Z"
sort.dir.za: "Z → A"
sort.dir.oldestFirst: "Oldest first"
sort.dir.newestFirst: "Newest first"
sort.dir.mostRecent: "Most recent first"
sort.dir.leastVisited: "Least visited first"
sort.dir.mostVisited: "Most visited first"
sort.neverOpened: "Never opened · {n}"
settings.bookmarks.defaultSort: "Default sort for new collections"
settings.bookmarks.defaultSortHelp: "Applied the first time you open a collection. Each collection then remembers its own preference until you reset it."
settings.bookmarks.resetDefault: "Reset to system default (newest first)"
settings.bookmarks.clickTrackingNote: "“Last clicked” and “Click count” use shared click data across all members of a collection. Your activity contributes to the same counts everyone else sees."
```

---

## Playwright E2E (`sort-preferences.spec.ts`)

- `default sort is newest first when nothing is configured`
- `selecting "Title A→Z" reorders bookmarks alphabetically`
- `preference persists across page reload (per collection)`
- `switching collections shows a different effective sort if one collection has a preference and the other doesn't`
- `click "Use as default" pins the current sort; a freshly opened second collection then uses it`
- `clicking a bookmark increments click count and bubbles it up under "Click count desc"` (relies on existing `trackClick` infra)
- `never-opened bookmarks appear under the "Never opened" divider when sorting by Last clicked`

Use `data-testid="bookmark-sort-trigger"` on the toolbar trigger and `data-testid="bookmark-sort-option-{FIELD}"` on each option for stable selectors.

---

## Acceptance checklist

- [ ] `npm run generate-api` produces `SortField`, `SortDirection`, `UserSettingsResourceApi`, extended `CollectionSettingsJson`, extended `UserInfoJson`.
- [ ] `useSortStore` exists with `effective`, `hydrateUserDefault`, `hydrateCollection`, `setForCurrentCollection`, `resetForCurrentCollection`, `setUserDefault`, `pinAsUserDefault`.
- [ ] On `/auth/me` success, user default hydrates.
- [ ] On collection load, that collection's sort settings hydrate.
- [ ] `BookmarkSortMenu` renders in the sticky toolbar with current field + direction visible on the trigger button.
- [ ] Picking a field updates the list immediately (optimistic) and persists to backend.
- [ ] Direction toggle inside the active row flips ASC/DESC without closing the menu.
- [ ] "Reset" only appears when a per-collection override exists; clicking it calls DELETE.
- [ ] "Use as default" pins the current effective sort as user default.
- [ ] Last-clicked / click-count sorts group never-opened bookmarks under a clear divider.
- [ ] Stable tie-breaker on `createdAt desc`.
- [ ] Settings dialog "Bookmarks" section reads / writes user default; reset clears it.
- [ ] Shared click data is explicitly mentioned in the Settings copy.
- [ ] All E2E specs pass; `npm run type-check` clean.
- [ ] No regression in existing search / filter behaviour — sort runs after the existing filter chain.

---

## Out of scope

- Search relevance sort. We have no relevance score yet; revisit when FR-071 (search operators) lands.
- Per-user click tracking. Aggregate behaviour is documented for users in Settings.
- Sorting inside the `grouped` layout's groups — that layout already imposes its own ordering by tag/folder. Sort field still applies *within* each group; verify it works (it should, since `BookmarkGroupedLayout.vue` consumes the same `filteredBookmarks`).
- Server-side sorting / pagination. Frontend remains the source of truth for ordering.
