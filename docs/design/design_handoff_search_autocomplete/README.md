# PR — FR-072 Search Operator Autocomplete

**Goal.** As the user types in the header `SearchBar`, a smart dropdown appears that suggests available tags, folders, and property values — so users can discover and use the operator language (`#tag`, `tag:`, `folder:`, `property:key=val`) without memorising syntax.

**Scope.** Pure frontend feature. No API changes are required — all suggestion data is already available in the existing Pinia stores. The changes are contained to `SearchBar.vue`, a new composable, and a new dropdown component.

---

## About the design files

`FR-072 Search Autocomplete.html` in this folder is a **high-fidelity HTML prototype** — it is the visual and behavioural specification. It is **not** production code to ship directly. The task is to recreate the designs and interactions inside the existing Vue/TypeScript/Tailwind codebase using its established patterns (shadcn/vue, Pinia, lucide-vue-next, vue-i18n).

Open the HTML file in a browser and interact with it before reading the rest of this document. The Tweaks panel (bottom-left) has one-click presets to trigger each autocomplete mode.

---

## Fidelity

**High-fidelity.** Match the prototype pixel-closely:
- Dropdown border radius, shadow, animation, padding
- Header row with mode icon + label + keyboard hint badges
- Per-row layout: color dot (tags) or mode icon + prefix (muted) + value (highlighted match)
- Footer syntax-reference strip
- Keyboard badge pills (↑↓, ↵ Tab, Esc)

Use `var(--primary)` / Tailwind `primary` tokens for all blue accents. The dark/light theme already works via the existing CSS variables — no extra work needed.

---

## Architecture overview

Three additions, one modification:

| Item | Type | Purpose |
|---|---|---|
| `useSearchAutocomplete.ts` | Composable | Parses the query + cursor, returns suggestions |
| `SearchAutocompleteDropdown.vue` | Component | The dropdown UI |
| `SearchBar.vue` | **Modified** | Mounts the dropdown, wires keyboard events |
| Tokenizer/matcher | **Modified** | Add `tag:` as an alias for `#` |

---

## Files touched

| File | Change |
|---|---|
| `frontend/src/composables/useSearchAutocomplete.ts` | **New.** Core parsing + suggestion logic. |
| `frontend/src/components/ui/SearchAutocompleteDropdown.vue` | **New.** Dropdown UI component. |
| `frontend/src/components/ui/SearchBar.vue` | Modified — mount dropdown, handle keyboard events, cursor tracking. |
| `frontend/src/lib/searchTokenizer.ts` | Modified (or create) — add `tag:` token kind alongside `#`. |
| `frontend/src/stores/bookmark.ts` | Possibly read-only — verify that `availableTags`, `availableFolders`, `availablePropertyKeys` are exposed (see Data Sources section). |
| `frontend/src/locales/*.json` | Add `search.autocomplete.*` keys. |

---

## Trigger rules

| User types… | Autocomplete mode | Suggestions source |
|---|---|---|
| `#` or `#qu…` | Tags | All tag names in current collection |
| `tag:` or `tag:qu…` | Tags | Same as above |
| `folder:` or `folder:de…` | Folders | All folder names in current collection |
| `under:` or `under:de…` | Folders (hierarchical) | All folder names — matches folder and all subfolders |
| `property:` or `property:st…` | Property keys | All property key names on bookmarks in collection |
| `property:status=` or `property:status=dr…` | Property values | All known values for that specific key |
| `fo`, `ta`, `prop`, `un` (≥2 chars, no colon yet) | Operator discovery | Fixed list: `folder:`, `tag:`, `property:`, `under:` |
| Anything else | — | Dropdown hidden |

The dropdown appears **only for the token at the cursor** — not for other tokens already committed in the query.

---

## 1. `useSearchAutocomplete.ts`

```ts
// frontend/src/composables/useSearchAutocomplete.ts
import { computed } from 'vue'
import { useCollectionStore } from '@/stores/collection'  // adjust import to wherever
                                                           // tags/folders/properties are exposed
export interface AcItem {
  key: string
  label: string
  insert: string          // full text to replace the current token with
  type: 'tag' | 'folder' | 'prop-key' | 'prop-val' | 'operator'
  color?: string          // tag swatch hex, e.g. "#dc2626"
  propKey?: string        // for prop-val items
  filter: string          // substring that was typed — used by Highlight component
  hint?: string           // right-aligned secondary label
}

export interface AcResult {
  mode: 'tag' | 'folder' | 'under' | 'prop-key' | 'prop-val' | 'operator'
  label: string           // dropdown header text, e.g. "Tags", "Folders"
  items: AcItem[]
  range: [number, number] // [tokenStart, tokenEnd] — the slice of query to replace
}

export function useSearchAutocomplete() {
  const collection = useCollectionStore()   // <-- see Data Sources section

  // All values are computed from the store so they stay reactive
  const allTags    = computed(() => collection.availableTags)       // { name, color }[]
  const allFolders = computed(() => collection.availableFolders)    // string[]
  const allPropKeys= computed(() => collection.availablePropKeys)   // string[]
  const allPropVals= computed(() => collection.availablePropVals)   // Record<string, string[]>

  function parse(query: string, cursor: number): AcResult | null {
    const before     = query.slice(0, cursor)
    const lastSp     = Math.max(before.lastIndexOf(' '), before.lastIndexOf('\t'))
    const tokenStart = lastSp + 1
    const token      = before.slice(tokenStart)
    const after      = query.slice(cursor)
    const nextSp     = after.search(/\s/)
    const tokenEnd   = cursor + (nextSp === -1 ? after.length : nextSp)
    const range: [number, number] = [tokenStart, tokenEnd]
    const tl         = token.toLowerCase()

    // ── # or tag: → Tags
    if (token.startsWith('#') || tl.startsWith('tag:')) {
      const filter = token.startsWith('#') ? token.slice(1).toLowerCase() : token.slice(4).toLowerCase()
      const prefix = token.startsWith('#') ? '#' : 'tag:'
      const items  = allTags.value
        .filter(t => !filter || t.name.toLowerCase().includes(filter))
        .map(t => ({
          key: t.name, label: t.name, insert: prefix + t.name,
          type: 'tag' as const, color: t.color, filter,
        }))
      return { mode: 'tag', label: 'Tags', items, range }
    }

    // ── folder: → Folders
    if (tl.startsWith('folder:')) {
      const filter = token.slice(7).toLowerCase()
      const items  = allFolders.value
        .filter(f => !filter || f.toLowerCase().includes(filter))
        .map(f => ({
          key: f, label: f, insert: 'folder:' + f,
          type: 'folder' as const, filter,
        }))
      return { mode: 'folder', label: 'Folders', items, range }
    }

    // ── property: → Property keys or values
    if (tl.startsWith('property:')) {
      const rest  = token.slice(9)
      const eqIdx = rest.indexOf('=')
      if (eqIdx === -1) {
        // key mode
        const filter = rest.toLowerCase()
        const items  = allPropKeys.value
          .filter(k => !filter || k.toLowerCase().includes(filter))
          .map(k => ({
            key: k, label: k, insert: 'property:' + k + '=',
            type: 'prop-key' as const, hint: 'then =value', filter,
          }))
        return { mode: 'prop-key', label: 'Properties', items, range }
      } else {
        // value mode
        const propKey  = rest.slice(0, eqIdx)
        const valFilter= rest.slice(eqIdx + 1).toLowerCase()
        const vals     = allPropVals.value[propKey] ?? []
        const items    = vals
          .filter(v => !valFilter || v.toLowerCase().includes(valFilter))
          .map(v => ({
            key: v, label: v, insert: 'property:' + propKey + '=' + v,
            type: 'prop-val' as const, propKey, filter: valFilter,
          }))
        return { mode: 'prop-val', label: `"${propKey}" values`, items, range }
      }
    }

    // ── Operator discovery: "fo", "ta", "prop" etc.
    if (token.length >= 2 && !token.includes(':')) {
      const OPS = [
        { trigger: 'tag',      full: 'tag:',       hint: 'filter by tag (also #)' },
        { trigger: 'folder',   full: 'folder:',    hint: 'filter by folder name' },
        { trigger: 'under',    full: 'under:',     hint: 'filter by folder (includes subfolders)' },
        { trigger: 'property', full: 'property:',  hint: 'filter by property value' },
      ]
      const matched = OPS.filter(op => op.trigger.startsWith(tl) && tl !== op.trigger)
      if (matched.length) {
        return {
          mode: 'operator',
          label: 'Operators',
          items: matched.map(op => ({
            key: op.full, label: op.full, insert: op.full,
            type: 'operator' as const, hint: op.hint, filter: token,
          })),
          range,
        }
      }
    }

    return null
  }

  return { parse }
}
```

---

## 2. `SearchAutocompleteDropdown.vue`

```vue
<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { Hash, Folder, Box, Zap } from 'lucide-vue-next'
import type { AcResult, AcItem } from '@/composables/useSearchAutocomplete'

const props = defineProps<{
  result: AcResult
  activeIdx: number
}>()
const emit  = defineEmits<{ select: [item: AcItem] }>()
const listEl = ref<HTMLElement | null>(null)

// Scroll active item into view on keyboard navigation
watch(() => props.activeIdx, async () => {
  await nextTick()
  listEl.value?.querySelector('.ac-sel')?.scrollIntoView({ block: 'nearest' })
})

const modeIcon = { tag: Hash, folder: Folder, 'prop-key': Box, 'prop-val': Box, operator: Zap }
</script>

<template>
  <div
    class="absolute top-[calc(100%+6px)] left-0 right-0 z-50 overflow-hidden rounded-[10px]
           border border-border bg-popover shadow-[0_16px_48px_rgba(0,0,0,.35)]
           animate-in fade-in-0 slide-in-from-top-1 duration-100"
    @mousedown.prevent
  >
    <!-- Header -->
    <div class="flex items-center justify-between border-b border-border px-2.5 py-1.5">
      <div class="flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        <component :is="modeIcon[result.mode]" class="h-[11px] w-[11px] opacity-70" />
        {{ result.label }}
      </div>
      <div v-if="result.items.length" class="flex gap-1">
        <kbd class="rounded border border-border bg-muted px-1 py-px text-[9.5px] text-muted-foreground">↑↓</kbd>
        <kbd class="rounded border border-border bg-muted px-1 py-px text-[9.5px] text-muted-foreground">↵ Tab</kbd>
        <kbd class="rounded border border-border bg-muted px-1 py-px text-[9.5px] text-muted-foreground">Esc</kbd>
      </div>
    </div>

    <!-- List -->
    <div ref="listEl" class="max-h-56 overflow-y-auto">
      <div v-if="!result.items.length" class="px-2.5 py-3 text-center text-xs text-muted-foreground">
        No matches
      </div>
      <button
        v-for="(item, idx) in result.items"
        :key="item.key + idx"
        :class="[
          'ac-item flex w-full items-center gap-2 px-2.5 py-[5px] text-left transition-colors',
          idx === activeIdx ? 'ac-sel bg-primary/10 text-primary' : 'text-foreground hover:bg-primary/10',
        ]"
        type="button"
        @click="emit('select', item)"
      >
        <!-- Tag color dot -->
        <span
          v-if="item.type === 'tag' && item.color"
          class="h-2 w-2 shrink-0 rounded-sm"
          :style="{ background: item.color }"
        />
        <!-- Mode icon for non-tag items -->
        <component
          v-else
          :is="modeIcon[result.mode]"
          :class="['h-[11px] w-[11px] shrink-0', idx === activeIdx ? 'text-primary' : 'text-muted-foreground']"
        />

        <!-- Label -->
        <span class="flex-1 font-mono text-[12px]">
          <span :class="idx === activeIdx ? 'text-primary/60' : 'text-muted-foreground'">
            <template v-if="item.type === 'tag'">#</template>
            <template v-else-if="item.type === 'folder'">folder:</template>
            <template v-else-if="item.type === 'prop-key'">property:</template>
            <template v-else-if="item.type === 'prop-val'">property:{{ item.propKey }}=</template>
          </span>
          <!-- Highlighted match -->
          <AcHighlight :text="item.label" :filter="item.filter" />
        </span>

        <span v-if="item.hint" class="shrink-0 text-[10.5px] text-muted-foreground font-sans">
          {{ item.hint }}
        </span>
      </button>
    </div>

    <!-- Footer: syntax reference -->
    <div class="flex flex-wrap gap-2 border-t border-border px-2.5 py-1.5 font-mono text-[10.5px] text-muted-foreground">
      <span><b class="font-semibold text-foreground/75">#</b>tag</span>
      <span class="opacity-40">or</span>
      <span><b class="font-semibold text-foreground/75">tag:</b>name</span>
      <span class="opacity-30">·</span>
      <span><b class="font-semibold text-foreground/75">folder:</b>name</span>
      <span class="opacity-30">·</span>
      <span><b class="font-semibold text-foreground/75">property:</b>k=v</span>
      <span class="opacity-30">·</span>
      <span><b class="font-semibold text-foreground/75">-</b>negate</span>
    </div>
  </div>
</template>
```

### `AcHighlight` sub-component

Create `frontend/src/components/ui/AcHighlight.vue` — wraps matched substring in a highlighted `<mark>`:

```vue
<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ text: string; filter: string }>()

const parts = computed(() => {
  if (!props.filter) return [{ text: props.text, match: false }]
  const idx = props.text.toLowerCase().indexOf(props.filter.toLowerCase())
  if (idx === -1) return [{ text: props.text, match: false }]
  return [
    { text: props.text.slice(0, idx),                          match: false },
    { text: props.text.slice(idx, idx + props.filter.length),  match: true  },
    { text: props.text.slice(idx + props.filter.length),       match: false },
  ]
})
</script>

<template>
  <span>
    <template v-for="p in parts" :key="p.text + p.match">
      <mark v-if="p.match" class="rounded-[2px] bg-primary/20 px-px text-inherit">{{ p.text }}</mark>
      <template v-else>{{ p.text }}</template>
    </template>
  </span>
</template>
```

---

## 3. `SearchBar.vue` — modifications

Three things to add inside the existing component:

### 3a. Track cursor position and compute autocomplete

```ts
import { ref, computed } from 'vue'
import { useSearchAutocomplete } from '@/composables/useSearchAutocomplete'

const inputEl  = ref<HTMLInputElement | null>(null)
const cursor   = ref(0)
const acIdx    = ref(0)
const acResult = ref<AcResult | null>(null)
const acMouseDown = ref(false)

const { parse } = useSearchAutocomplete()

function refreshAc(val: string, pos?: number) {
  const c = pos ?? inputEl.value?.selectionStart ?? val.length
  const r = parse(val, c)
  acResult.value = r && r.items.length > 0 ? r : null
  acIdx.value    = 0
}

function onInput(e: Event) {
  const val = (e.target as HTMLInputElement).value
  emit('update:modelValue', val)
  refreshAc(val, (e.target as HTMLInputElement).selectionStart ?? undefined)
}

function onClick() {
  refreshAc(props.modelValue, inputEl.value?.selectionStart ?? undefined)
}
```

### 3b. Keyboard handler

Add to the existing `@keydown` handler (or create one):

```ts
function onKeyDown(e: KeyboardEvent) {
  if (!acResult.value) return

  const n = acResult.value.items.length
  if (e.key === 'ArrowDown') { e.preventDefault(); acIdx.value = Math.min(acIdx.value + 1, n - 1) }
  else if (e.key === 'ArrowUp')  { e.preventDefault(); acIdx.value = Math.max(acIdx.value - 1, 0) }
  else if ((e.key === 'Enter' || e.key === 'Tab') && n > 0) {
    e.preventDefault()
    commit(acResult.value.items[acIdx.value])
  }
  else if (e.key === 'Escape') acResult.value = null
}
```

### 3c. Token commit (insert selected suggestion)

```ts
function commit(item: AcItem) {
  if (!acResult.value) return
  const [s, e] = acResult.value.range
  const q      = props.modelValue

  // No trailing space for property:key= — user types the value next.
  // Add a space after all other completions.
  const suffix  = item.insert.endsWith('=') ? '' : ' '
  const tail    = q.slice(e).replace(/^\s+/, '')   // strip leading whitespace after the old token
  const newQuery= q.slice(0, s) + item.insert + suffix + tail
  const newCursor = s + item.insert.length + suffix.length

  emit('update:modelValue', newQuery)
  acResult.value = null

  nextTick(() => {
    inputEl.value?.focus()
    inputEl.value?.setSelectionRange(newCursor, newCursor)

    // Chain: after property:key= is inserted, immediately show value suggestions
    const follow = parse(newQuery, newCursor)
    if (follow && follow.items.length > 0) {
      acResult.value = follow
      acIdx.value    = 0
    }
  })
}
```

### 3d. Blur handling

```ts
function onBlur() {
  // acMouseDown prevents blur from closing the dropdown when the user
  // clicks a suggestion — @mousedown.prevent on the dropdown covers this
  // automatically (focus never leaves the input). This fallback is for
  // clicks outside both the input and the dropdown.
  if (!acMouseDown.value) acResult.value = null
}
```

### 3e. Mount the dropdown in the template

Wrap the existing input in a `relative` container and render the dropdown below it:

```html
<div class="relative w-full">
  <input
    ref="inputEl"
    v-bind="$attrs"
    :value="modelValue"
    @input="onInput"
    @keydown="onKeyDown"
    @click="onClick"
    @blur="onBlur"
  />

  <SearchAutocompleteDropdown
    v-if="acResult"
    :result="acResult"
    :active-idx="acIdx"
    @select="commit"
  />
</div>
```

---

## 4. Tokenizer — add `tag:` support

Find wherever `#tagname` tokens are parsed (likely `searchTokenizer.ts` or inside the bookmark store filter). Add a branch for `tag:name`:

```ts
// Existing: #tagname → { kind: 'tag', value: 'tagname' }
// Add:      tag:name  → { kind: 'tag', value: 'name' }

const re = /(-)?(#([\w-]+)|tag:([\w-]+)|property:([\w-]+)(=|>=|<=|>|<)([^\s]+)|([a-z]+):([^\s]+)|"([^"]*)"|(\S+))/gi
// Update match-group indices accordingly, or use two separate passes.
```

Then in the filter/matcher, both token kinds already resolve to `kind === 'tag'` — no further changes to matching logic.

---

## 5. Data sources

The composable needs three computed collections from the store. Verify which store exposes them and adjust the import:

| Data | Expected shape | Likely source |
|---|---|---|
| Available tags | `{ name: string; color: string }[]` | `useCollectionStore()` or `useTagStore()` — the sidebar already renders tags so the data is there |
| Available folders | `string[]` | `useCollectionStore().folderTree` — flatten to names |
| Property keys | `string[]` | Derived from `bookmarkStore.bookmarks` — `[...new Set(bookmarks.flatMap(b => Object.keys(b.properties ?? {})))]` |
| Property values per key | `Record<string, string[]>` | Same derivation — group by key, collect unique values |

If tags/folders are not yet reactive in a store, derive them from `bookmarkStore.bookmarks` the same way — the prototype does exactly this (see `AC_TAGS`, `AC_FOLDERS`, `AC_PROP_VALS` at the top of the JS in the HTML file).

---

## Mobile behaviour

The dropdown is absolutely positioned inside the `search-shell` wrapper. On mobile (`<md`), the search lives inside a Sheet/drawer. The dropdown renders inside the Sheet so it will clip if the Sheet has `overflow: hidden` — set `overflow: visible` on the Sheet content container or use a portal (`Teleport`) to mount the dropdown to `body` with manual positioning on mobile.

The existing keyboard-event handling works on mobile virtual keyboards for the `↵` key. `Tab` is not available on iOS/Android — this is expected; `Enter` is the sole confirm key on mobile.

---

## i18n keys to add

```json
{
  "search": {
    "autocomplete": {
      "tags":       "Tags",
      "folders":    "Folders",
      "properties": "Properties",
      "noMatches":  "No matches",
      "hintNav":    "↑↓",
      "hintConfirm":"↵ Tab",
      "hintDismiss":"Esc"
    }
  }
}
```

---

## Design tokens

All values come from the existing Tailwind / shadcn token set — no new tokens needed.

| Role | Token |
|---|---|
| Dropdown background | `bg-popover` |
| Dropdown border | `border-border` |
| Active row background | `bg-primary/10` |
| Active row text | `text-primary` |
| Prefix text (muted) | `text-muted-foreground` |
| Highlight mark | `bg-primary/20` |
| Dropdown shadow | `shadow-[0_16px_48px_rgba(0,0,0,.35)]` |
| Border radius | `rounded-[10px]` |
| Font for labels | `font-mono text-[12px]` |
| Font for hints | `font-sans text-[10.5px]` |

---

## Acceptance checklist

- [ ] Typing `#` opens the tag dropdown immediately; filtering works as you type.
- [ ] Typing `tag:` opens the same tag dropdown.
- [ ] Typing `folder:` opens the folder dropdown.
- [ ] Typing `property:` opens the property-key dropdown; selecting a key (e.g. `status=`) immediately chains to the value dropdown.
- [ ] Typing `fo`, `ta`, or `prop` (≥ 2 chars) shows the operator discovery row.
- [ ] Matched characters are highlighted in each suggestion row.
- [ ] ↑↓ move selection; the active item scrolls into view in a long list.
- [ ] ↵ or Tab confirms the selected item and positions the cursor after the inserted token.
- [ ] Esc closes the dropdown without changing the query.
- [ ] Clicking a suggestion inserts the token and keeps focus in the input.
- [ ] Clicking outside the dropdown (not on a suggestion) closes it.
- [ ] After inserting a `property:key=` token, the value dropdown opens immediately without a keypress.
- [ ] Inserting a tag / folder / value token adds a trailing space and subsequent tokens are preserved.
- [ ] `tag:vacme` filters results identically to `#vacme`.
- [ ] Dropdown does not appear mid-word for plain text (e.g. typing `quarkus` with no trigger).
- [ ] Works in the desktop header `SearchBar` (variant="header").
- [ ] Works in the mobile search Sheet.
- [ ] No layout shift in the header when the dropdown opens.
- [ ] `npm run type-check` clean.
- [ ] Existing search E2E specs pass (the dropdown should not intercept Enter when closed).

---

## Out of scope (future)

- Negation autocomplete (`-#` prefix) — the parser already supports `-`, but no autocomplete is triggered.
- OR / AND grouping operators.
- Recent-query history dropdown (separate FR).
- Inline ghost-text completion (separate exploration).

---

## Reference

The interactive prototype is at `FR-072 Search Autocomplete.html` in this folder. The complete `parseAutocomplete` function, `AutocompleteDropdown` component, and `Highlight` component are in the `<script type="text/babel">` block — use them as the authoritative reference for edge-case behaviour (token boundary detection, chaining, suffix logic).
