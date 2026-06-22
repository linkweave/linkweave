# PR 1 — Move Search Into the Header

**Goal.** Lift the search input out of the bookmark-list body and into the global header. Header becomes the single home for collection-wide controls (collection switcher, search, +Add, user menu). The body recovers ~80px of vertical space.

**Scope.** Pure frontend refactor. No API, no schema, no Pinia store changes. Search query already lives in `useBookmarkStore.searchQuery` — we are only relocating its UI.

---

## Files touched

| File | Change |
|---|---|
| `frontend/src/components/layout/HeaderCl.vue` | Add a centred `#search` slot between leading and actions. |
| `frontend/src/components/layout/MainLayout.vue` | Forward a `header-search` slot through to `HeaderCl`. |
| `frontend/src/components/ui/SearchBar.vue` | Add a compact `variant="header"` style (smaller height, max-width). |
| `frontend/src/components/ui/HeaderSearchMobile.vue` | **New.** Icon button + sheet wrapper for mobile. Active-state styling when search is non-empty. |
| `frontend/src/components/bookmark/SearchActiveChip.vue` | **New.** Filter chip shown above the bookmark list when search is active — "prod foo · 12 results [×]". |
| `frontend/src/views/CollectionView.vue` | Pass `<SearchBar>` into the new `header-search` slot. Render `<SearchActiveChip>` above `<BookmarkList>`. Remove the inline search above the list. |
| `frontend/src/views/CleanupSuggestionsView.vue`, `TrashbinView.vue`, `CollectionManageView.vue` | If they render search, do the same move. (Most don't — verify.) |
| `frontend/e2e/*.spec.ts` | Update Playwright selectors that look for the search input in the body. |
| `frontend/src/locales/*` | (Optional) Tighten placeholder for the narrower input. |

---

## Mobile behaviour — explicit spec

The header has limited horizontal real estate on phones (the leading group already holds hamburger + logo + collection switcher). We do **not** try to inline a 200px text input there.

| Breakpoint | Behaviour |
|---|---|
| `≥ lg` (≥1024px) | Search renders as a 480–560px input, centred in the header. Always visible, sticky by virtue of the header. |
| `md` (640–1023px) | Search renders as a narrower input (~280px max), still in the header. Placeholder shortens to `"Search…"`. |
| `< md` (<640px) | Search collapses to an icon-only button in the header actions group. Tapping it opens a full-width **Sheet** (shadcn/vue `Sheet`) sliding down from the top, with a focused input + cancel button. Submit / Esc closes the sheet. The bookmark list below shows filtered results live as the user types (same `v-model` to `bookmarkStore.searchQuery`). |

Why a Sheet, not an inline expanding input: keeps the header layout stable, gives the input a real keyboard target with a visible cancel affordance, and matches the existing mobile sidebar drawer pattern in `MainLayout.vue`.

**Keyboard.** `⌘K` / `Ctrl+K` focuses the input on desktop; opens the sheet on mobile. Add a global listener in `MainLayout.vue` (or a dedicated `useSearchHotkey` composable). `Esc` clears focus / closes the sheet.

---

## Concrete diffs

### 1. `HeaderCl.vue` — add the search slot

```vue
<template>
  <header class="relative z-[60] flex items-center gap-3 md:gap-4 p-3 md:p-4 border-b border-border bg-card shrink-0">
    <!-- Leading: hamburger + logo + collection switcher -->
    <div class="flex items-center gap-2 md:gap-3 shrink-0">
      <slot name="leading" />
      <router-link to="/" class="shrink-0 cursor-pointer">
        <img :src="logoUrl" alt="" class="h-6 w-6" />
      </router-link>
      <slot name="title">
        <CollectionSwitcher />
      </slot>
    </div>

    <!-- Centre: search (desktop) -->
    <div class="hidden md:flex flex-1 justify-center min-w-0">
      <div class="w-full max-w-[560px]">
        <slot name="search" />
      </div>
    </div>

    <!-- Spacer when no search slot is provided -->
    <div class="flex-1 md:hidden" />

    <!-- Actions: mobile search icon + +Add + user menu -->
    <div class="flex items-center gap-1 md:gap-2 shrink-0">
      <slot name="search-mobile" />
      <slot name="actions" />
      <UserMenuCl v-if="auth.isAuthenticated" />
    </div>
  </header>
</template>
```

### 2. `MainLayout.vue` — forward the slots

In the `<HeaderCl>` block, add:

```vue
<template #search>
  <slot name="header-search" />
</template>
<template #search-mobile>
  <slot name="header-search-mobile" />
</template>
```

### 3. `SearchBar.vue` — `variant="header"` prop

Add a `variant?: 'default' | 'header'` prop. When `header`, render with `h-9` (36px), no surrounding label, a `⌘K` kbd badge on the right (hidden `<lg`), and `text-sm`. Keep the existing `v-model` contract — drop-in compatible.

### 4. `HeaderSearchMobile.vue` — new component

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { Search, X } from 'lucide-vue-next'
import { ButtonCl, SearchBar } from '@/components/ui'
import { useBookmarkStore } from '@/stores/bookmark'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
const open = ref(false)
</script>

<template>
  <ButtonCl variant="ghost" size="icon" class="md:hidden" aria-label="Search" @click="open = true">
    <Search class="h-5 w-5" />
  </ButtonCl>

  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-[70] md:hidden">
      <!-- Light dim only — no blur. Live search results stay crisp underneath. -->
      <div class="absolute inset-0 bg-background/40" @click="open = false" />
      <div class="relative bg-card border-b border-border p-3 flex items-center gap-2">
        <SearchBar
          v-model="bookmarkStore.searchQuery"
          :placeholder="t('search.placeholder')"
          autofocus
          class="flex-1"
        />
        <ButtonCl variant="ghost" size="icon" @click="open = false">
          <X class="h-5 w-5" />
        </ButtonCl>
      </div>
    </div>
  </Teleport>
</template>
```

(If you have shadcn/vue `Sheet` wired up, swap the inline overlay for `<Sheet side="top">` — same behaviour, less code.)

#### 4a. Active-state styling on the trigger

When `bookmarkStore.searchQuery` is non-empty, the search icon button must be visually distinct so the user knows results are filtered after the sheet closes. In `HeaderSearchMobile.vue`:

```vue
<script setup lang="ts">
import { computed, ref } from 'vue'
import { Search, X } from 'lucide-vue-next'
import { ButtonCl, SearchBar } from '@/components/ui'
import { useBookmarkStore } from '@/stores/bookmark'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
const open = ref(false)
const hasQuery = computed(() => bookmarkStore.searchQuery.trim().length > 0)
</script>

<template>
  <ButtonCl
    variant="ghost"
    size="icon"
    class="md:hidden relative"
    :class="hasQuery ? 'bg-primary/10 border border-primary/30 text-primary hover:bg-primary/15' : ''"
    aria-label="Search"
    @click="open = true"
  >
    <Search class="h-5 w-5" />
    <span
      v-if="hasQuery"
      class="absolute top-1.5 right-1.5 h-2 w-2 rounded-full bg-primary ring-2 ring-card"
      aria-hidden="true"
    />
  </ButtonCl>
  ...
</template>
```

Apply the equivalent `bg-primary/10` ring on the desktop `SearchBar` `variant="header"` when query is non-empty so behaviour is consistent across breakpoints.

### 5. `SearchActiveChip.vue` — new component

Visible above `<BookmarkList>` whenever search is active. Tap the body to re-open the sheet (or focus the desktop input); tap the `×` to clear.

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { Search, X } from 'lucide-vue-next'
import { useBookmarkStore } from '@/stores/bookmark'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
const hasQuery = computed(() => bookmarkStore.searchQuery.trim().length > 0)
const resultCount = computed(() => bookmarkStore.filteredBookmarks.length)

function reopen() {
  // Focus desktop input or open mobile sheet — both listen for this event.
  window.dispatchEvent(new CustomEvent('linkweave:focus-search'))
}
function clear() {
  bookmarkStore.searchQuery = ''
}
</script>

<template>
  <div
    v-if="hasQuery"
    :class="[
      'flex items-center gap-2 py-2 text-sm border border-primary/30',
      // mobile: sticky, full-bleed, slight blur so it reads over scrolling cards
      'sticky top-0 z-30 -mx-4 px-4 rounded-none border-x-0 bg-primary/10 backdrop-blur',
      // desktop: static, contained, normal corners
      'md:static md:mx-0 md:px-3 md:rounded-md md:border-x md:bg-primary/5 md:backdrop-blur-none',
    ]"
    role="status"
    aria-live="polite"
  >
    <Search class="h-4 w-4 text-primary shrink-0" />
    <button
      type="button"
      class="flex-1 min-w-0 text-left truncate text-foreground hover:text-primary"
      @click="reopen"
    >
      <span class="font-medium">"{{ bookmarkStore.searchQuery }}"</span>
      <span class="text-muted-foreground"> · {{ t('search.resultCount', { n: resultCount }) }}</span>
    </button>
    <ButtonCl variant="ghost" size="icon" class="h-7 w-7" :aria-label="t('search.clear')" @click="clear">
      <X class="h-4 w-4" />
    </ButtonCl>
  </div>
</template>
```

Wire the `linkweave:focus-search` listener in both `SearchBar.vue` (desktop, calls `inputRef.value?.focus()`) and `HeaderSearchMobile.vue` (mobile, sets `open = true`). Cheaper than a Pinia field and scoped per-page automatically.

Add locale entries:

```
search.resultCount: "{n} result | {n} results"   # use vue-i18n pluralization
search.clear: "Clear search"
```

### 6. `CollectionView.vue` — wire it up, remove inline search

```vue
<template>
  <MainLayout>
    <template #header-search>
      <SearchBar
        v-model="bookmarkStore.searchQuery"
        :placeholder="t('search.placeholder')"
        variant="header"
      />
    </template>
    <template #header-search-mobile>
      <HeaderSearchMobile />
    </template>
    <template #header-actions>
      <ButtonCl size="sm" :disabled="offline.isOffline" @click="isAddingBookmark = true">
        <BookmarkPlus class="h-4 w-4 sm:mr-2" />
        <span class="hidden sm:inline">{{ t('header.addBookmark') }}</span>
      </ButtonCl>
    </template>

    <div :class="[containerClass, 'mx-auto space-y-6']">
      <FolderBreadcrumbCl />
      <!-- Search removed from here -->
      <BookmarkList />
    </div>

    <CreateBookmarkDialog … />
  </MainLayout>
</template>
```

Add `HeaderSearchMobile` and `SearchActiveChip` imports. Render the chip between the breadcrumb and the list:

```vue
<div :class="[containerClass, 'mx-auto space-y-4']">
  <FolderBreadcrumbCl />
  <SearchActiveChip />
  <BookmarkList />
</div>
```

### 7. `⌘K` global hotkey

Drop a `useSearchHotkey()` composable into `MainLayout.vue`:

```ts
import { onMounted, onBeforeUnmount } from 'vue'
function onKey(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    document.querySelector<HTMLInputElement>('[data-search-input]')?.focus()
  }
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
```

Stamp `data-search-input` on the underlying `<input>` in `SearchBar.vue`.

---

## E2E updates

Search any Playwright spec for selectors like `getByPlaceholder('Search')` or by-position locators landing on the body and rescope them to the header. If you have a `searchBookmark` page-object helper, this is a one-line change.

---

## Acceptance checklist

- [ ] Desktop ≥1024px: search visible in header centre, max-width 560px, sticky with the header.
- [ ] Tablet 640–1023px: narrower header search, still inline.
- [ ] Mobile <640px: search becomes an icon button; tapping opens a top sheet with focused input.
- [ ] When search is non-empty: the trigger (mobile icon button OR desktop input) shows an active state (tinted bg/border + dot badge on mobile).
- [ ] When search is non-empty: a `SearchActiveChip` appears above the bookmark list with the term, result count, and a one-tap clear button.
- [ ] Tapping the chip body re-focuses the input / re-opens the mobile sheet.
- [ ] On mobile, the chip stays pinned to the top of the viewport while scrolling a long filtered list. On desktop it scrolls with content.
- [ ] No search input rendered in the body of `CollectionView.vue` (the chip is *not* an input).
- [ ] `⌘K` / `Ctrl+K` focuses the input (or opens the sheet on mobile).
- [ ] Existing search behaviour unchanged — same debounced `bookmarkStore.searchQuery` write path.
- [ ] All E2E specs pass (`npx playwright test --project=chromium`).
- [ ] `npm run type-check` clean.
- [ ] Accessibility: search input has a label, mobile button has `aria-label`, sheet traps focus.

---

## Out of scope (future PRs)

- Sticky toolbar with breadcrumb + view/sort controls (PR 2 — see `docs/plans/sort-preferences-handoff.md` once written).
- Search operators / `FR-071` parser. This PR keeps the existing search semantics.
- Recent searches / history dropdown. Future enhancement.

---

## Test the prototype

A clickable hi-fi mock of the proposed layout (with a "current vs proposed" toggle in the bottom-right Tweaks panel) lives at `Sort Preferences.html` in the design project. Hand that file to Claude Code as visual reference.
