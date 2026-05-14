# PR 2 — Sticky Toolbar (breadcrumb + view + sort controls)

**Goal.** Introduce a thin, sticky row directly under the global header that holds the breadcrumb (left) and view/sort/settings controls (right). This row becomes the home for *view*-scoped controls — anything that changes how the current bookmark list is rendered, as opposed to *collection*-scoped controls in the header.

**Depends on.** PR 1 (search has moved into the header).
**Unblocks.** PR 5 (sort dropdown plugs into this toolbar).
**Scope.** Pure frontend refactor. No API, no store changes (`useUiStore.bookmarkLayout` already exists). We're consolidating UI surfaces.

---

## What lives where after this PR

| Surface | Holds | Why |
|---|---|---|
| **Header** (existing, sticky already) | Collection switcher · search · +Add · user menu | Collection-scoped, global actions. Always visible. |
| **Toolbar** (new, sticky under header) | Breadcrumb · layout toggle (grid/list/grouped) · sort dropdown (PR5) · settings icon | View-scoped controls for the bookmark list. Visible while scrolling so users can change view without scrolling back up. |
| **Bookmark list body** | Just the list. No more inline search (PR1), no floating breadcrumb (PR2). | Maximises vertical space for content. |

---

## Files touched

| File | Change |
|---|---|
| `frontend/src/components/bookmark/BookmarkListToolbar.vue` | **New.** Sticky toolbar component. |
| `frontend/src/components/folder/FolderBreadcrumbCl.vue` | Style-only tweak — render at toolbar font size (text-sm). Remove any outer margins so it sits flush in the toolbar. |
| `frontend/src/views/CollectionView.vue` | Mount `<BookmarkListToolbar>` between header and `<BookmarkList>`. Remove the direct `<FolderBreadcrumbCl>` render. Move the `<SearchActiveChip>` to render *under* the toolbar (so the toolbar is the outermost sticky row, chip stacks underneath when active). |
| `frontend/src/components/bookmark/index.ts` | Export `BookmarkListToolbar`. |
| `frontend/e2e/*.spec.ts` | Update any tests that locate breadcrumb or layout buttons by their old DOM position. |

---

## `BookmarkListToolbar.vue` — full spec

```vue
<script setup lang="ts">
import { LayoutGrid, List, Rows3, Settings } from 'lucide-vue-next'
import { FolderBreadcrumbCl } from '@/components/folder'
import { ButtonCl } from '@/components/ui'
import { useUiStore } from '@/stores/ui'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const ui = useUiStore()
</script>

<template>
  <div
    class="sticky top-0 z-30 flex items-center gap-3 h-11 px-4 sm:px-6
           border-b border-border
           bg-background/80 backdrop-blur supports-[backdrop-filter]:bg-background/70"
    data-testid="bookmark-list-toolbar"
  >
    <!-- Left: breadcrumb (truncates with min-w-0 + overflow on the breadcrumb itself) -->
    <div class="flex-1 min-w-0">
      <FolderBreadcrumbCl />
    </div>

    <!-- Right: view controls + sort slot + settings -->
    <div class="flex items-center gap-1 shrink-0">
      <div class="flex items-center rounded-md border border-border bg-card overflow-hidden">
        <ButtonCl
          variant="ghost" size="icon"
          :class="['h-7 w-7 rounded-none', ui.bookmarkLayout === 'grid' ? 'bg-accent text-accent-foreground' : 'text-muted-foreground']"
          :aria-label="t('toolbar.layoutGrid')"
          :aria-pressed="ui.bookmarkLayout === 'grid'"
          @click="ui.setBookmarkLayout('grid')"
        >
          <LayoutGrid class="h-3.5 w-3.5" />
        </ButtonCl>
        <ButtonCl
          variant="ghost" size="icon"
          :class="['h-7 w-7 rounded-none', ui.bookmarkLayout === 'list' ? 'bg-accent text-accent-foreground' : 'text-muted-foreground']"
          :aria-label="t('toolbar.layoutList')"
          :aria-pressed="ui.bookmarkLayout === 'list'"
          @click="ui.setBookmarkLayout('list')"
        >
          <List class="h-3.5 w-3.5" />
        </ButtonCl>
        <ButtonCl
          variant="ghost" size="icon"
          :class="['h-7 w-7 rounded-none', ui.bookmarkLayout === 'grouped' ? 'bg-accent text-accent-foreground' : 'text-muted-foreground']"
          :aria-label="t('toolbar.layoutGrouped')"
          :aria-pressed="ui.bookmarkLayout === 'grouped'"
          @click="ui.setBookmarkLayout('grouped')"
        >
          <Rows3 class="h-3.5 w-3.5" />
        </ButtonCl>
      </div>

      <!-- Sort dropdown lands here in PR5 -->
      <slot name="sort" />

      <!-- Settings (hidden until there's something to put behind it; reveal in PR5) -->
      <slot name="extras" />
    </div>
  </div>
</template>
```

### Sticky stacking

Header is `relative z-[60]` and sits at the top of a flex column (`MainLayout.vue`). The toolbar's `sticky top-0` pins it to the *scroll container* — the `<main>` element. That means:

- Header stays visible (it's the flex sibling above the scroll container, not inside it).
- Toolbar stays pinned to the top of the scroll viewport.
- They visually stack — header above toolbar — without z-fighting.

If you find the toolbar slipping under the header during scroll, the issue is that `<main>` isn't the scroll container; check `MainLayout.vue` — `main` already has `overflow-y-auto`, so this should work out of the box.

### Mobile layout

At `<sm` the layout-toggle group should hide (`hidden sm:flex` on the wrapping div) and reveal as an icon-button menu instead. Most mobile users don't switch layouts mid-session, and the toolbar gets cramped fast. Cheap fallback:

```vue
<div class="hidden sm:flex items-center ...">
  <!-- the 3 layout buttons -->
</div>
<ButtonCl variant="ghost" size="icon" class="sm:hidden h-7 w-7" :aria-label="t('toolbar.changeLayout')">
  <!-- icon that reflects current layout; tap → opens shadcn DropdownMenu with the 3 options -->
</ButtonCl>
```

(Implementation detail: reuse shadcn/vue's `DropdownMenu`; same one PR5 uses for the sort menu.)

---

## `CollectionView.vue` — wire-up diff

```diff
 <template>
   <MainLayout>
     <template #header-search>...</template>
     <template #header-search-mobile>...</template>
     <template #header-actions>...</template>

-    <div :class="[containerClass, 'mx-auto space-y-4']">
-      <FolderBreadcrumbCl />
-      <SearchActiveChip />
-      <BookmarkList />
-    </div>
+    <!-- Toolbar is outside the centred container so it can be full-bleed sticky.
+         Content stays in the container. -->
+    <BookmarkListToolbar />
+    <div :class="[containerClass, 'mx-auto space-y-4 px-4 sm:px-6 pt-4']">
+      <SearchActiveChip />
+      <BookmarkList />
+    </div>
   </MainLayout>
 </template>
```

Note: `<SearchActiveChip>` was sticky in PR1 — that still works. The two sticky elements stack: toolbar at `top-0` (pins first), chip *also* `top-0` but scrolls into view *under* the toolbar because the chip sits below it in DOM order. If you want the chip to pin *below* the toolbar instead of disappearing under it, set the chip's `top-11` on mobile (44px toolbar height) — adjust in `SearchActiveChip.vue`:

```diff
- 'sticky top-0 z-30 -mx-4 px-4 ...',
+ 'sticky top-11 z-20 -mx-4 px-4 ...',
```

Lower `z-20` so the toolbar (`z-30`) stays above when they overlap during scroll-bounce.

---

## Locale additions

```
toolbar.layoutGrid: "Grid view"
toolbar.layoutList: "List view"
toolbar.layoutGrouped: "Grouped view"
toolbar.changeLayout: "Change layout"
```

---

## Acceptance checklist

- [ ] Toolbar renders directly under the header, full-width, ~44px tall.
- [ ] Toolbar stays pinned to the top of the scroll viewport as the bookmark list scrolls. Header remains above it (no overlap).
- [ ] Breadcrumb truncates with ellipsis when too long for the toolbar's left flex region; never overflows into the controls.
- [ ] Layout-toggle group (grid/list/grouped) reflects `useUiStore.bookmarkLayout` and updates the list immediately on click.
- [ ] `aria-pressed` on the active layout button.
- [ ] `<sm`: layout-toggle group collapses to a single icon-button dropdown.
- [ ] Sort + Settings slots render as placeholders (empty) — PR5 fills them.
- [ ] `<SearchActiveChip>` (from PR1) still appears above the list when search is non-empty, and on mobile sticks *below* the toolbar, not under it.
- [ ] Existing E2E specs pass; any tests selecting the breadcrumb by position have been rescoped to `data-testid="bookmark-list-toolbar"`.
- [ ] `npm run type-check` clean.

---

## Out of scope

- The sort dropdown itself — that's PR5.
- Multi-select toolbar (FR-078) — replaces or augments this toolbar in selection mode. Future PR.
- Saved-search/smart-collection pin button (FR-076) — future.

---

## Design reference

The clickable hi-fi prototype lives at `Sort Preferences.html` (or wherever you committed it). The toolbar visible in the "proposed" layout mode is what this PR ships — minus the sort dropdown for now.
