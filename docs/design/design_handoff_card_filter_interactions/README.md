# Handoff: Card click affordance + Tag/Folder filter interactions

## Overview

This handoff covers two coupled UX changes in LinkWeave's bookmark list:

1. **Whole-card click opens the bookmark** — replaces today's behavior where only the URL anchor and kebab are clickable, while the card visually advertises (with `cursor-grab`, hover ring, and primary-tinted border) that the entire surface is interactive.
2. **Clickable tag chips and folder labels filter the list** — clicking a `#tag` chip on a card toggles a `#tagname` filter in the search bar; clicking the "in folder" label toggles `folder:foldername`. This is the first slice of **UC-070 (Search with Operators)** delivered through direct manipulation on cards, plus a new multi-pill rendering of the `SearchActiveChip` to show the active filters.

The two are coupled because making the whole card a link conflicts with having interactive children — solving #1 requires the **stretched-link pattern** described below, which also unlocks #2.

## About the design files

The file in `prototype/` is a **design reference**, not production code. It is a standalone HTML page that mocks the new interactions using inline React + Babel to keep it self-contained.

The task is to **recreate these interactions in the existing Vue 3 / Pinia / Tailwind / radix-vue stack** in `frontend/src/`, reusing established patterns and stores. Do not lift the React code or inline styles. The CSS selectors in the prototype refer to the same Tailwind design tokens that are already defined in the codebase (`bg-card`, `border-border`, `text-muted-foreground`, etc.), so class names should translate directly.

## Fidelity

**High-fidelity.** The prototype uses the codebase's actual `@theme` tokens (verbatim from `frontend/src/assets/...` or wherever `@theme` lives), including the agreed-upon **lifted-popover fix** (`--color-popover: #2a2e3a` in dark mode — see "Out of scope" below if this hasn't shipped yet). Spacing, type sizes, border colors, and hover states should match the prototype exactly. Layout and component structure should follow the existing Vue components.

## Files in scope

| Path | Change |
|---|---|
| `frontend/src/components/bookmark/BookmarkCard.vue` | Restructure to use stretched-link pattern; tags + folder label become buttons that toggle filters |
| `frontend/src/components/bookmark/SearchActiveChip.vue` | Replace single-pill rendering with multi-pill rendering of the parsed query |
| `frontend/src/stores/bookmark.ts` (or wherever `searchQuery` and `filteredBookmarks` live) | Add a query parser (UC-070 subset) and route the `filteredBookmarks` getter through it. Add helpers for toggling tokens. |
| `docs/use_cases/UC-070-search-with-operators.md` | Reference doc — already exists. This handoff implements the **tag + folder + note + free-text + negation** subset. |

## Out of scope (explicitly deferred)

The following UC-070 operators are **NOT** part of this handoff and should be left as `// TODO UC-070` placeholders in the parser:

- `property:value` operator
- `created:`, `created:>`, `created:<` (date operators per BR-084 / BR-085)
- `match:OR` logic switching (BR-081)
- Autocomplete dropdown (alternative flow A1, BR-083)
- Invalid-operator syntax tooltips (alternative flow A2)
- Sidebar filter combination (alternative flow A4) — sidebar tag click should still produce the same `#tag` token in the query, but no special "sidebar mode" is needed.

The parser **must** handle the rest gracefully so unimplemented operators don't crash the search — they should be parsed as tokens but treated as a no-op match (always true) until implemented. See the prototype's `matches()` for the pattern.

---

## Pattern 1 — Stretched-link card

### Today's structure (`BookmarkCard.vue`)

```vue
<div class="group ... cursor-grab" :draggable="!isTouch" @dragstart="..." @dragend="...">
  <div class="flex items-start gap-3">
    <BookmarkFavicon .../>
    <div class="flex-1 min-w-0">
      <div class="flex items-center gap-2">
        <h3>{{ title }}</h3>
        <button class="...kebab...">...</button>      <!-- only this is clickable -->
        <DropdownMenuRoot v-else>...</DropdownMenuRoot>
      </div>
      <a :href="url" target="_blank" @click="trackClick">{{ url }}</a>  <!-- only this opens the link -->
      <p>{{ description }}</p>
      <div>
        <span>in {{ folderName }}</span>                <!-- not interactive -->
        <span v-for="tagId">...</span>                  <!-- not interactive -->
      </div>
    </div>
  </div>
</div>
```

### New structure

```vue
<div
  class="group relative rounded-lg border border-border bg-card p-4
         hover:ring-2 hover:ring-primary/50 hover:border-primary/30
         transition-[box-shadow,border-color,color] duration-150
         text-muted-foreground hover:text-accent-foreground
         cursor-grab active:cursor-grabbing
         focus-within:ring-2 focus-within:ring-primary"
  :draggable="!isTouch"
  @dragstart="onBookmarkDragStart"
  @dragend="onBookmarkDragEnd"
>
  <!-- The stretched link: invisible, covers the entire card surface -->
  <a
    :href="bookmark.data.url"
    target="_blank"
    rel="noopener noreferrer"
    :aria-label="bookmark.data.title"
    class="absolute inset-0 rounded-[inherit] z-0 outline-none"
    @click="bookmarkStore.trackClick(bookmark.id)"
  />

  <!-- Content layer: pointer-events-none so clicks fall through to the stretched <a>.
       Interactive children re-enable pointer events with .pointer-events-auto + relative z-10. -->
  <div class="relative flex items-start gap-3 pointer-events-none">
    <BookmarkFavicon ... />
    <div class="flex-1 min-w-0">
      <div class="flex items-center gap-2">
        <h3 class="font-medium text-foreground truncate">{{ bookmark.data.title }}</h3>

        <!-- Kebab: pointer-events-auto + z-10 so it sits above the stretched link -->
        <button
          v-if="!menuActivated"
          class="ml-auto h-8 w-8 ... pointer-events-auto relative z-10
                 hover:bg-accent hover:text-accent-foreground"
          @click.stop="menuActivated = true"
        >
          <MoreHorizontal class="h-4 w-4" />
        </button>
        <DropdownMenuRoot v-else :default-open="true">
          <DropdownMenuTrigger as-child>
            <button class="... pointer-events-auto relative z-10 hover:bg-accent hover:text-accent-foreground" @click.stop>
              <MoreHorizontal class="h-4 w-4" />
            </button>
          </DropdownMenuTrigger>
          <!-- ...menu content unchanged... -->
        </DropdownMenuRoot>
      </div>

      <!-- URL: visible text, NOT a link anymore (the whole card is the link).
           Keep it as a span so the surrounding stretched-link click handles it. -->
      <div class="flex items-center gap-1 text-sm text-muted-foreground mt-0.5">
        <span class="truncate">{{ bookmark.data.url }}</span>
        <ExternalLink class="h-3 w-3 shrink-0" />
      </div>

      <p v-if="bookmark.data.description" class="text-sm text-muted-foreground mt-2 line-clamp-2">
        {{ bookmark.data.description }}
      </p>

      <!-- Tag chips + folder label are now BUTTONS that toggle filters.
           pointer-events-auto + relative z-10 stacks them above the stretched link. -->
      <div v-if="hasTagsOrFolder" class="flex flex-wrap items-center gap-1 mt-2">
        <button
          v-if="folderName"
          type="button"
          class="pointer-events-auto relative z-10
                 inline-flex items-center gap-1 px-2 py-0.5 rounded-full
                 text-xs text-muted-foreground border border-dashed border-border
                 hover:text-foreground hover:border-foreground hover:bg-secondary
                 transition-colors"
          :class="{ 'text-foreground border-solid border-foreground bg-secondary': isFolderFilterActive }"
          @click.stop="onToggleFolderFilter"
          :title="`Filter by folder: ${folderName}`"
        >
          <Folder class="h-3 w-3" />
          <span>in {{ folderName }}</span>
        </button>

        <button
          v-for="tagId in props.bookmark.data.tagIds"
          :key="tagId"
          type="button"
          class="pointer-events-auto relative z-10
                 inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs
                 bg-secondary text-foreground border border-transparent
                 hover:bg-[color-mix(in_oklab,var(--tag-color)_14%,var(--color-secondary))]
                 hover:border-[var(--tag-color)]
                 transition-colors"
          :class="tagClass(tagId)"
          :style="{ '--tag-color': getTagById(tagId)?.data.color ?? '#64748b' }"
          @click="onTagClick($event, tagId)"
          :title="tagTitle(tagId)"
        >
          <span class="h-2 w-2 rounded-sm" :style="{ background: getTagById(tagId)?.data.color ?? '#64748b' }" />
          {{ getTagById(tagId)?.data.name ?? tagId.substring(0, 8) }}
        </button>
      </div>
    </div>
  </div>
</div>
```

Where `tagClass()` returns active / excluded variants:

```ts
function tagClass(tagId: string) {
  const tag = getTagById(tagId)
  if (!tag) return ''
  if (bookmarkStore.isTagExcluded(tag.data.name)) return 'opacity-70 line-through border-dashed'
  if (bookmarkStore.isTagActive(tag.data.name)) return 'bg-[color-mix(in_oklab,var(--tag-color)_22%,var(--color-secondary))] border-[var(--tag-color)]'
  return ''
}
```

### Critical correctness details

- **The `<a>` must NOT wrap interactive children.** Nesting `<button>` inside `<a>` is invalid HTML and breaks Cmd-click. The stretched link is a sibling of the content layer, both inside the outer card `<div>`.
- **The stretched link is keyboard-focusable.** Tab focus moves directly to the card. Enter activates the link. Do not add `tabindex` or `role` to the outer `<div>`.
- **The visible focus ring is on the outer `<div>`**, achieved via `focus-within:` (Tailwind's `focus-within` variant). This is intentional — focusing the stretched link rings the whole card, which matches the click target.
- **`@click.stop` on every interactive child** so the click doesn't bubble up to the stretched link.

### Drag-and-drop interaction (CRITICAL — verify on all browsers)

The existing card supports drag-and-drop into folders via the sidebar. This must continue to work. The stretched-link pattern changes the event surface, so the following rules apply:

1. **`:draggable` and `@dragstart`/`@dragend` stay on the OUTER `<div>`**, not on the stretched `<a>`. The existing handler chain is unchanged:
   ```ts
   function onBookmarkDragStart(event: DragEvent) {
     if (!event.dataTransfer) return
     event.dataTransfer.effectAllowed = 'move'
     event.dataTransfer.setData(DRAG_TYPE_BOOKMARK, props.bookmark.id)
     setDraggingBookmark(true)
   }
   ```
2. **The stretched `<a>` itself is draggable by default** because anchors are natively draggable. This produces a ghost of the URL string, which would override our intended card-ghost drag. Suppress it: add `draggable="false"` on the stretched `<a>`.
   ```html
   <a class="absolute inset-0 ..." draggable="false" ... />
   ```
3. **Content layer `pointer-events-none` does NOT block drag events** — `dragstart` originates from the outer div which still has pointer events. Verified in the prototype.
4. **The drag ghost (preview image) is the entire card by default** because the outer `<div>` is what's draggable. If the ghost looks wrong (e.g. shows only a fragment on Safari), explicitly set it:
   ```ts
   event.dataTransfer.setDragImage(event.currentTarget as HTMLElement, 0, 0)
   ```
5. **Click vs drag distinction:** A drag that ends without a drop still fires a `click` on the source element. The stretched link's `@click` would then incorrectly open the URL. Mitigate by tracking drag state and short-circuiting the click:
   ```ts
   let didDrag = false
   function onBookmarkDragStart(e: DragEvent) { didDrag = true; /* ...rest unchanged... */ }
   function onBookmarkDragEnd() { setDraggingBookmark(false); setTimeout(() => { didDrag = false }, 0) }
   function onCardLinkClick(e: MouseEvent) { if (didDrag) { e.preventDefault(); return } bookmarkStore.trackClick(...) }
   ```
   The `setTimeout(..., 0)` resets the flag after the synthetic click fires.
6. **Touch:** `:draggable="!isTouch"` is unchanged. Tapping a card on touch devices triggers the stretched link's click → opens the URL.

**Manual test matrix for the reviewer:**
- [ ] Chrome desktop: drag card → sidebar folder = move; drag card and release in empty space = no navigation; click card = opens URL.
- [ ] Firefox desktop: same as above.
- [ ] Safari desktop: same as above; check the drag ghost looks correct.
- [ ] Touch device: tap card = opens URL (no drag UI); long-press should not start a drag (verify `isTouch` correctly disables draggable).

### Tag click behavior

```ts
function onTagClick(event: MouseEvent, tagId: string) {
  event.preventDefault()
  event.stopPropagation()
  const tag = getTagById(tagId)
  if (!tag) return
  const modifier = (event.altKey || event.shiftKey) ? 'exclude' : undefined
  bookmarkStore.toggleQueryToken({ kind: 'tag', value: tag.data.name }, modifier)
}
```

- **Plain click**: toggles `#tagname` between absent and present in the query string. If the tag is currently excluded (`-#tagname`), a plain click removes it entirely.
- **Alt or Shift + click**: replaces any current state with `-#tagname` (excluded).
- **Click on an already-active tag**: removes it.

This is the **power-user modifier** decision — `⌥/⇧+click` for exclude is intentionally invisible; tooltips advertise it on hover. If telemetry shows nobody discovers this, we may surface it via a tag-chip context menu in a later iteration.

### Folder click behavior

```ts
function onToggleFolderFilter() {
  if (!folderName) return
  bookmarkStore.toggleQueryToken({ kind: 'op', key: 'folder', value: folderName })
}
```

Folder labels have no modifier — plain click toggles `folder:foldername`.

---

## Pattern 2 — Multi-pill SearchActiveChip

### Today's `SearchActiveChip.vue`

Renders a single pill showing the raw `bookmarkStore.searchQuery` string verbatim with a clear-all button.

### New behavior

The store parses `searchQuery` into a list of tokens. The chip renders **one pill per token**, each individually dismissible. The strip also retains a "Clear all" affordance and the result count.

```vue
<template>
  <div
    v-if="tokens.length > 0"
    :class="[
      'flex flex-wrap items-center gap-2 py-2 px-3 text-sm border border-primary/30 rounded-md',
      'bg-primary/5',
      // Preserve existing responsive sticky behavior:
      'sticky -top-3 z-30 -mx-3 px-3 rounded-none border-x-0 bg-primary/10 backdrop-blur',
      'sm:-top-6 sm:-mx-6 sm:px-6',
      'md:static md:top-auto md:mx-0 md:px-3 md:rounded-md md:border-x md:bg-primary/5 md:backdrop-blur-none',
    ]"
    role="status"
    aria-live="polite"
  >
    <span class="inline-flex items-center gap-1.5 text-primary text-xs font-medium shrink-0">
      <Search class="h-3.5 w-3.5" />
      {{ t('search.filters') }}
    </span>

    <FilterPill
      v-for="(token, i) in tokens"
      :key="i"
      :token="token"
      @remove="bookmarkStore.removeQueryTokenAt(i)"
    />

    <span class="ml-auto text-muted-foreground text-xs shrink-0">
      {{ t('search.resultCount', { n: resultCount }) }}
    </span>
    <button class="text-xs text-muted-foreground hover:text-foreground px-1.5 py-0.5 rounded" @click="clear">
      {{ t('search.clearAll') }}
    </button>
  </div>
</template>
```

A new `FilterPill.vue` component renders each token with the appropriate icon + label. See the prototype's `TokenPill` for the visual reference. Token kinds to handle initially: `tag`, `op (folder|note)`, `text`, and negation for any of them.

### What changes in the store

Pseudocode for `useBookmarkStore`:

```ts
// New: parsed tokens, derived from searchQuery
const queryTokens = computed(() => tokenize(searchQuery.value))

// Existing filteredBookmarks now routes through tokens
const filteredBookmarks = computed(() =>
  allBookmarks.value.filter(b => matchesTokens(b, queryTokens.value))
)

// Helpers used by BookmarkCard + SearchActiveChip
function toggleQueryToken(token: QueryToken, modifier?: 'exclude') {
  const next = applyToggle(queryTokens.value, token, modifier)
  searchQuery.value = stringifyTokens(next)
}
function removeQueryTokenAt(idx: number) {
  const next = queryTokens.value.filter((_, i) => i !== idx)
  searchQuery.value = stringifyTokens(next)
}
function isTagActive(name: string) {
  return queryTokens.value.some(t => t.kind === 'tag' && !t.neg && t.value.toLowerCase() === name.toLowerCase())
}
function isTagExcluded(name: string) {
  return queryTokens.value.some(t => t.kind === 'tag' && t.neg && t.value.toLowerCase() === name.toLowerCase())
}
```

The **source of truth remains the `searchQuery` string** per UC-070 BR-082 (client-side eval against the Pinia store). Tokens are a derived view. This makes URL-sharing of filtered states a future feature with no migration needed.

### Tokenizer

A working tokenizer is in the prototype (`tokenize()` function). Port it to a TypeScript module — suggested location `frontend/src/lib/searchQuery.ts` — with exported types:

```ts
export type QueryToken =
  | { kind: 'tag'; value: string; neg: boolean }
  | { kind: 'op'; key: string; value: string; neg: boolean }   // folder, note, property (future), created (future)
  | { kind: 'text'; value: string; neg: boolean }

export function tokenize(query: string): QueryToken[]
export function stringifyTokens(tokens: QueryToken[]): string
export function toggleToken(tokens: QueryToken[], token: QueryToken, modifier?: 'exclude'): QueryToken[]
export function matchesTokens(b: BookmarkJson, tokens: QueryToken[], ctx: MatchContext): boolean
```

The tokenizer should also be **unit-tested** — UC-070 BR-079 calls out edge cases (quoted strings, nested quotes) that are easy to regress. Suggested test cases:
- `""` → `[]`
- `"hello"` → 1 text token
- `"#tag"` → 1 tag token
- `"-#tag"` → 1 negated tag
- `'#"two words"'` → 1 tag with value `two words`
- `"#a folder:b -c"` → 3 tokens
- `"folder:'multi word'"` → 1 op token, key=folder, value=`multi word` (quoted variant)

---

## Visual design tokens used

All values come from the codebase's existing `@theme` and `.dark` blocks. **No new tokens are introduced by this handoff** beyond what is already approved for the lifted popover work:

| Use | Token | Dark hex | Light hex |
|---|---|---|---|
| Card bg | `--color-card` | `#1e2028` | `#ffffff` |
| Card hover ring | `--color-primary` at 50% | `#3b82f6` | `#2563eb` |
| Tag chip bg (neutral) | `--color-secondary` | `#252a32` | `#f3f4f6` |
| Tag chip bg (active) | mix(`--tag-color` 22%, `--color-secondary`) | computed | computed |
| Tag dot | `tag.data.color` (user-chosen) | varies | varies |
| Folder pill border (idle) | `--color-border` dashed | `#363d49` | `#e5e7eb` |
| Folder pill bg (active) | `--color-secondary` | `#252a32` | `#f3f4f6` |
| Filter strip bg | `--color-primary` at 5–10% | computed | computed |
| Filter strip border | `--color-primary` at 30% | computed | computed |

The tag chip uses `color-mix(in oklab, var(--tag-color) <pct>%, var(--color-secondary))` so the user-chosen tag color shows through as a subtle tint without ever taking over the chip. This is intentional — the chip stays readable regardless of which tag color the user picked. (We discussed but rejected filled colored chips for the contrast reasons documented in the design conversation.)

---

## Acceptance criteria

A reviewer should verify all of the following:

### Card interaction
- [ ] Clicking anywhere on a card body opens the bookmark URL in a new tab.
- [ ] `bookmarkStore.trackClick(id)` is called on card click.
- [ ] Cmd-click (Mac) / Ctrl-click (Windows) opens in a new tab without losing the current view.
- [ ] Right-click → "Open in new tab" works.
- [ ] Tab key focuses the card; a visible ring shows on the card; Enter opens the URL.
- [ ] Clicking the kebab button does NOT open the URL; it opens the dropdown menu.
- [ ] Clicking a dropdown menu item (Edit / Move / Delete) does NOT open the URL.
- [ ] Hover ring and border-tint state appears on hover.
- [ ] The card's drag cursor (`cursor-grab`) remains.

### Drag-and-drop
- [ ] Drag a card into a sidebar folder → bookmark moves (existing behavior preserved).
- [ ] Drag a card and release outside any drop target → nothing happens (no navigation, no error).
- [ ] The drag ghost shows the card content, not just the URL string. (Set `draggable="false"` on the stretched `<a>` if not.)
- [ ] After a drag-without-drop, the next click on a card still opens the URL correctly (no stuck `didDrag` flag).
- [ ] Verified on Chrome, Firefox, Safari desktop.

### Tag chip
- [ ] Clicking a tag chip appends `#tagname` to the search query.
- [ ] Clicking an already-active tag chip removes it from the query.
- [ ] ⌥/⇧+click on a tag adds `-#tagname` (excluded) to the query.
- [ ] Active tag chips have a visible "selected" treatment.
- [ ] Excluded tag chips show a strikethrough / dashed border / reduced opacity.
- [ ] Tooltip on tag chip describes the click behavior including the modifier hint.

### Folder label
- [ ] Clicking the "in folder" label appends `folder:foldername` to the query.
- [ ] Clicking an already-active folder label removes it from the query.
- [ ] No modifier behavior on folder labels (plain click only).
- [ ] Active folder label has a visible "selected" treatment.

### Multi-pill filter strip
- [ ] When the search query has tokens, the filter strip shows one pill per token.
- [ ] Each pill's [×] removes just that token from the query string.
- [ ] "Clear all" removes the entire query.
- [ ] Result count updates live.
- [ ] Sticky behavior on mobile / static on desktop is preserved from the current `SearchActiveChip`.
- [ ] Typing operators directly into the search bar (`#quarkus folder:insel -#prod`) produces matching pills.
- [ ] Unrecognized operators (`property:foo`, `created:>today-30d`) parse without crashing — they appear as pills and match all bookmarks (no-op) until implemented.

### Accessibility
- [ ] Stretched-link `aria-label` reads the bookmark title.
- [ ] Tag chip `<button>`s announce their state ("filter by tag X", "remove filter X").
- [ ] Pills in the filter strip are keyboard-navigable; their [×] buttons are reachable via Tab.
- [ ] Filter strip has `role="status" aria-live="polite"` (unchanged from today).

---

## Reference files

- **`prototype/Card and Filter Pattern.html`** — working demo of the full interaction. Open in a browser, toggle dark/light in the bottom-left panel, try the quick-test queries, click cards / tags / folder labels.
- **`screenshots/`** — visual reference for each state:
  - `01-idle-state.png` — no filters active, all bookmarks shown.
  - `02-tag-filter-active.png` — `#quarkus` filter applied. Active tag chips on visible cards are highlighted; the multi-pill filter strip is shown.
  - `03-combined-filters.png` — three pills in the filter strip: `#vacme`, `folder:vacme-prod`, and the excluded `-#uat` (rendered with the destructive-tinted variant).
  - `04-empty-state.png` — filter combination matches nothing.
  - `05-card-hover.png` — card hover state with the visible ring, primary-tinted border, and the kebab button surfaced.
- **`docs/use_cases/UC-070-search-with-operators.md`** — full spec for the operator syntax. This handoff implements a subset (tags, folder, note, free-text, negation).

## Open questions / follow-ups

- Tag color readability still depends on the user's color choice for the dot. The outline-style chip (current design) sidesteps the text-on-tag-color contrast issue we discussed, but the dot itself can still be low-contrast on the card. Consider a future iteration that constrains `ColorInputCl` to a curated palette.
- The `match:OR` operator (UC-070 BR-081) changes how free-text terms combine. Confirm with PM whether to ship the OR-mode UI control or wait for autocomplete (A1) to land first.
- Autocomplete (A1, BR-083) is the next big slice. The tokenizer in this handoff is structured so the autocomplete UI can read `queryTokens.value` plus the input cursor position to suggest completions.
