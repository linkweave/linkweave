# UC-067 — Bookmark Properties: Frontend

**Goal.** Add typed, per-collection custom fields ("properties") to bookmarks. Users can define properties on a collection, set values on individual bookmarks, see those values as inline badges on cards, and filter the bookmark list using `property:key=value` search syntax.

**Depends on.** UC-067 backend (endpoints listed below must be live). Regenerate the API client after the backend lands: `npm run generate-api`.  
**Unblocks.** Nothing — this is the final UC-067 deliverable.  
**Scope.** Pure frontend. All backend endpoints are already implemented.

---

## Design reference

`UC-067 Properties.html` in the project root is a **high-fidelity interactive prototype** built in plain HTML/React. It is a design reference — not production code. Your task is to recreate the behaviour and visual design it shows, using the existing Vue + Pinia + Tailwind stack, following every pattern established in the codebase.

Open the file in a browser. The Tweaks panel (bottom-left) lets you open the settings modal and an edit dialog, and try `property:status=draft` / `property:priority>3` in the search box.

---

## API endpoints (already implemented)

| Method | Path | Body | Returns |
|---|---|---|---|
| `GET`    | `/api/collections/{id}/property-definitions`        | —                          | `PropertyDefinitionJson[]` |
| `POST`   | `/api/collections/{id}/property-definitions`        | `PropertyDefinitionJson`   | `PropertyDefinitionJson`   |
| `PUT`    | `/api/collections/{id}/property-definitions/{defId}`| `PropertyDefinitionJson`   | `PropertyDefinitionJson`   |
| `DELETE` | `/api/collections/{id}/property-definitions/{defId}`| —                          | 204                        |
| `PUT`    | `/api/bookmarks/{id}/properties`                    | `Record<string, unknown>`  | `BookmarkJson` (updated)   |

---

## Frontend types

```ts
// frontend/src/types/property.ts

export type PropertyType =
  | 'text'
  | 'number'
  | 'boolean'
  | 'select'
  | 'multi-select'
  | 'date'

export interface PropertyDefinitionJson {
  id: string
  collectionId: string
  name: string            // machine-name, slug-style: "status", "needs-review"
  type: PropertyType
  options?: string[]      // only for select / multi-select
}

// Extend the existing BookmarkJson with:
// propertyValues?: Record<string, string | number | boolean>
// (confirm field name matches the generated DTO after `npm run generate-api`)
```

---

## Files touched

| File | Change |
|---|---|
| `frontend/src/api/generated/**` | Regenerate after backend lands. |
| `frontend/src/types/property.ts` | **New.** `PropertyDefinitionJson`, `PropertyType`. |
| `frontend/src/stores/property.ts` | **New.** Pinia store — fetch / create / update / delete definitions. |
| `frontend/src/stores/collection.ts` | On collection load, call `propertyStore.fetchForCollection(id)`. |
| `frontend/src/components/bookmark/CollectionSettingsModal.vue` | **New.** Gear icon in toolbar opens this modal (Display + Properties tabs). |
| `frontend/src/components/bookmark/BookmarkPropertyBadge.vue` | **New.** Single `key = value` chip on a card. |
| `frontend/src/components/bookmark/BookmarkPropertyInput.vue` | **New.** One form control for a property value, switches on type. |
| `frontend/src/components/bookmark/BookmarkCard.vue` | Extended — prop-row of `<BookmarkPropertyBadge>` chips below the tag row. |
| `frontend/src/components/bookmark/EditBookmarkDialog.vue` | Extended — inline Properties section below Tags, no extra tab. |
| `frontend/src/components/bookmark/BookmarkListToolbar.vue` | Extended — add gear icon button that emits `openSettings`. |
| `frontend/src/views/CollectionView.vue` | Wire `<CollectionSettingsModal>` and the gear emit. |
| `frontend/src/composables/useSearchTokenizer.ts` | Extended — parse `property:key=value/op` tokens. |
| `frontend/src/stores/bookmark.ts` | Extended — `filteredBookmarks` respects `PropertyToken` filter. |
| `frontend/src/components/bookmark/SearchActiveChip.vue` | Extended — render `PropertyToken` pills. |
| `frontend/src/locales/*.json` | New i18n strings (listed at end). |

---

## 1 — `usePropertyStore`

```ts
// frontend/src/stores/property.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { PropertyDefinitionResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { PropertyDefinitionJson } from '@/types/property'

const api = new PropertyDefinitionResourceApi(config)

export const usePropertyStore = defineStore('property', () => {
  const definitions = ref<PropertyDefinitionJson[]>([])
  const currentCollectionId = ref<string | null>(null)

  async function fetchForCollection(collectionId: string) {
    if (currentCollectionId.value === collectionId) return   // already loaded
    const defs = await api.apiCollectionsIdPropertyDefinitionsGet({ id: collectionId })
    definitions.value = defs
    currentCollectionId.value = collectionId
  }

  async function create(collectionId: string, payload: Omit<PropertyDefinitionJson, 'id' | 'collectionId'>) {
    const created = await api.apiCollectionsIdPropertyDefinitionsPost({
      id: collectionId,
      propertyDefinitionJson: { ...payload, collectionId },
    })
    definitions.value.push(created)
    return created
  }

  async function update(collectionId: string, defId: string, payload: Partial<PropertyDefinitionJson>) {
    const updated = await api.apiCollectionsIdPropertyDefinitionsDefIdPut({
      id: collectionId,
      defId,
      propertyDefinitionJson: payload as PropertyDefinitionJson,
    })
    const idx = definitions.value.findIndex(d => d.id === defId)
    if (idx !== -1) definitions.value[idx] = updated
    return updated
  }

  async function remove(collectionId: string, defId: string) {
    await api.apiCollectionsIdPropertyDefinitionsDefIdDelete({ id: collectionId, defId })
    definitions.value = definitions.value.filter(d => d.id !== defId)
  }

  function $reset() {
    definitions.value = []
    currentCollectionId.value = null
  }

  return { definitions, fetchForCollection, create, update, remove, $reset }
})
```

**Hydration wiring** — in `collection.ts`, after the active collection is set:

```ts
import { usePropertyStore } from '@/stores/property'
// ...
const propertyStore = usePropertyStore()
await propertyStore.fetchForCollection(collectionId)
```

---

## 2 — `CollectionSettingsModal.vue`

New modal opened by the gear `⚙` icon at the far-right of the sticky toolbar (after the Sort button and a `<div class="w-px h-4 bg-border mx-0.5"/>` divider).

### Structure

```
overlay (fixed inset-0, bg-black/55, backdrop-blur-sm)
└── dialog (max-w-[560px], rounded-xl, border, shadow-2xl)
    ├── header  — "Collection settings" title + subtitle + ✕ close
    ├── tab bar — [Display] [Properties]
    ├── body    — scrollable
    │   ├── Display tab
    │   │   ├── "Layout" section → grid/list icon toggle (reuses existing BookmarkLayoutToggle logic)
    │   │   └── "Properties" section → two toggle rows:
    │   │       · "Show badges on cards"  — localStorage key: linkweave:showPropertyBadges  (default false)
    │   │       · "Show in sidebar"       — localStorage key: linkweave:showPropertiesSidebar (default true)
    │   └── Properties tab
    │       ├── hint copy + code example
    │       ├── list of PropertyDefinitionRows (name | type badge | options preview | edit | delete)
    │       └── inline add/edit form (name, type selector, options input for select types)
    └── footer  — [Cancel] [Save properties | Done]
```

### Toggle row visual spec

Each toggle row is a `flex items-center justify-between gap-3 p-2.5 rounded-lg` container with `bg-secondary/60`.

- Left: name (`text-sm font-medium`) + description (`text-xs text-muted-foreground mt-0.5`)
- Right: `<Switch>` from your existing UI kit, or a custom `<input type="checkbox">` styled as a pill toggle (34×19 px, thumb 13 px, primary colour when checked, border-radius full)

### Property definition row

```
[monospace name]  [type badge]  [options preview truncated]  [edit icon]  [delete icon]
```

- Name: `font-mono text-[12.5px] font-medium`
- Type badge: `text-[10px] font-semibold uppercase tracking-[.04em] px-1.5 py-0.5 rounded bg-secondary text-muted-foreground border border-border`
- Options preview: `text-[11px] text-muted-foreground truncate max-w-[80px]`, values joined with ` · `
- Edit/delete: 24×24 icon buttons, `hover:bg-secondary`, delete gets `hover:text-destructive`

### Add/edit inline form (shown below the list)

```
dashed-border rounded-lg p-3 bg-primary/5 border border-primary/30
  Label: Name  →  <input> placeholder "e.g. status, priority, due…"
  Label: Type  →  flex-wrap button grid (text | number | boolean | select | multi-select | date)
                  Active type: bg-primary/12 border-primary text-primary
  Label: Options (only for select / multi-select)  →  <input> "draft, review, published"
  [Add property | Save changes]  [Cancel]
```

### Props / emits

```ts
// CollectionSettingsModal.vue
defineProps<{ open: boolean }>()
defineEmits<{ 'update:open': [value: boolean] }>()
```

State for Display toggles comes from two composables (see §5). The Properties tab reads/writes `usePropertyStore()` directly.

---

## 3 — `BookmarkCard.vue` — property badges

Below the existing tag/folder `meta-row`, add a conditional `prop-row`:

```vue
<div
  v-if="showPropertyBadges && visibleProps.length"
  class="flex flex-wrap gap-1.5 mt-1.5 pt-1.5 border-t border-border/45"
>
  <BookmarkPropertyBadge
    v-for="def in visibleProps"
    :key="def.name"
    :prop-def="def"
    :value="bookmark.propertyValues?.[def.name]"
    :active="isPropActive(def.name, bookmark.propertyValues?.[def.name])"
    @click="onPropBadgeClick(def.name, bookmark.propertyValues?.[def.name])"
  />
</div>
```

```ts
const propertyStore = usePropertyStore()
const showPropertyBadges = useShowPropertyBadges()   // composable — see §5

const visibleProps = computed(() =>
  propertyStore.definitions.filter(
    pd => bookmark.propertyValues?.[pd.name] !== undefined
       && bookmark.propertyValues?.[pd.name] !== ''
  )
)

function isPropActive(key: string, value: unknown) {
  // check if property:key=value token is in the current search query
  return bookmarkStore.searchQuery.includes(`property:${key}=${value}`)
}

function onPropBadgeClick(key: string, value: unknown) {
  // toggle property:key=value in the search query string
  const token = `property:${key}=${value}`
  if (bookmarkStore.searchQuery.includes(token)) {
    bookmarkStore.searchQuery = bookmarkStore.searchQuery.replace(token, '').trim()
  } else {
    bookmarkStore.searchQuery = (bookmarkStore.searchQuery + ' ' + token).trim()
  }
}
```

### `BookmarkPropertyBadge.vue`

```vue
<script setup lang="ts">
import type { PropertyDefinitionJson } from '@/types/property'
defineProps<{
  propDef: PropertyDefinitionJson
  value: unknown
  active?: boolean
}>()
</script>

<template>
  <button
    :class="[
      'inline-flex items-center rounded-[5px] text-[11px] px-2 py-0.5',
      'border bg-secondary transition-colors',
      active
        ? 'border-[#a78bfa]/50 bg-[#a78bfa]/10'
        : 'border-border hover:border-[#a78bfa]/45 hover:bg-[#a78bfa]/9',
      propDef.type === 'boolean' && displayValue === 'true'
        ? '!border-green-500/35 !bg-green-500/7'
        : '',
    ]"
    :title="`Filter by ${propDef.name}=${value}`"
  >
    <span class="text-muted-foreground mr-[3px]">{{ propDef.name }}</span>
    <span class="text-muted-foreground mx-[2px]">=</span>
    <span class="text-foreground font-medium">{{ displayValue }}</span>
  </button>
</template>
```

`displayValue` computed:
- `boolean`: `'true'` / `'false'`
- `date`: `new Date(value).toLocaleDateString('en-CH', { day: 'numeric', month: 'short' })`
- everything else: `String(value)`

---

## 4 — `EditBookmarkDialog.vue` — inline properties

Add a Properties section **below the tags row, above the save button**. No extra tab — properties live on the same form.

```vue
<!-- After the existing folder/tags fields -->
<template v-if="propertyStore.definitions.length">
  <div class="flex items-center gap-2.5 mt-4 mb-3">
    <span class="text-[11px] font-semibold uppercase tracking-[.06em] text-muted-foreground flex items-center gap-1.5">
      <CubeIcon class="h-3 w-3" /> Properties
    </span>
    <div class="flex-1 h-px bg-border" />
  </div>
  <BookmarkPropertyInput
    v-for="def in propertyStore.definitions"
    :key="def.name"
    :prop-def="def"
    :model-value="localProps[def.name]"
    @update:model-value="localProps[def.name] = $event"
    @clear="delete localProps[def.name]"
  />
</template>
```

On save, call `bookmarkStore.updateProperties(bookmark.id, localProps)` which calls `PUT /bookmarks/{id}/properties`.

### `BookmarkPropertyInput.vue`

Renders the right input for each `PropertyType`:

| Type | Control |
|---|---|
| `text` | `<input type="text">` |
| `number` | `<input type="number">` |
| `boolean` | Toggle switch (same component used in CollectionSettingsModal) |
| `select` | `<select>` with the definition's options |
| `multi-select` | Checkbox chip group |
| `date` | `<input type="date">` |

Each field shows a "Clear" text button (right-aligned in the label row) when a value is set.

```ts
defineProps<{ propDef: PropertyDefinitionJson; modelValue: unknown }>()
defineEmits<{ 'update:modelValue': [v: unknown]; clear: [] }>()
```

---

## 5 — localStorage composables

Two tiny composables so the preference is shared between `CollectionSettingsModal` and `BookmarkCard`:

```ts
// frontend/src/composables/usePropertyDisplayPrefs.ts
import { ref, watch } from 'vue'

function usePersisted(key: string, defaultValue: boolean) {
  const stored = localStorage.getItem(key)
  const state = ref(stored !== null ? stored === 'true' : defaultValue)
  watch(state, v => localStorage.setItem(key, String(v)))
  return state
}

export function useShowPropertyBadges() {
  return usePersisted('linkweave:showPropertyBadges', false)
}

export function useShowPropertiesSidebar() {
  return usePersisted('linkweave:showPropertiesSidebar', true)
}
```

---

## 6 — Search: `property:` token

### Tokenizer extension

The search query tokenizer (wherever it lives — likely `useBookmarkStore` or a dedicated composable) needs to recognise this shape:

```
property:key=value
property:key>3
property:key>=3
property:key<3
property:key<=3
```

Add a regex branch **before** the generic `key:value` branch:

```ts
// Regex fragment to add:
/property:([\w-]+)(=|>=|<=|>|<)([^\s]+)/

// Resulting token shape:
type PropertyToken = {
  kind: 'property'
  propKey: string        // e.g. "status"
  op: '=' | '>' | '<' | '>=' | '<='
  propValue: string      // e.g. "draft"
  neg: boolean
}
```

### Filter logic

In `filteredBookmarks` (currently in `bookmark.ts`), add a branch for `PropertyToken`:

```ts
case 'property': {
  const raw = bookmark.propertyValues?.[token.propKey]
  const bv = raw === undefined ? '' : String(raw)
  if (token.op === '=')  ok = bv.toLowerCase() === token.propValue.toLowerCase()
  if (token.op === '>')  ok = Number(bv) > Number(token.propValue)
  if (token.op === '<')  ok = Number(bv) < Number(token.propValue)
  if (token.op === '>=') ok = Number(bv) >= Number(token.propValue)
  if (token.op === '<=') ok = Number(bv) <= Number(token.propValue)
  break
}
```

### Filter pill rendering (`SearchActiveChip.vue`)

Property tokens render as a pill with:
- Icon: `<CubeIcon class="h-3 w-3" style="color: #a78bfa" />`
- Content: `<span class="text-muted-foreground">key</span> <span class="text-muted-foreground mx-0.5">op</span> value`
- Same remove (×) button as other pills
- Class `pill property` (add to existing pill styles)

Stringify back: `property:${propKey}${op}${propValue}` (quote value if it contains spaces).

---

## 7 — Sidebar

When `useShowPropertiesSidebar()` is `true`, render a **Properties** section in `SidebarCl.vue` below Folders, above Tags:

```vue
<template v-if="showPropertiesSidebar && propertyStore.definitions.length">
  <SidebarSectionHeading>{{ t('sidebar.properties') }}</SidebarSectionHeading>
  <SidebarRow
    v-for="def in propertyStore.definitions"
    :key="def.id"
    @click="bookmarkStore.searchQuery = (bookmarkStore.searchQuery + ` property:${def.name}=`).trim()"
  >
    <template #icon>
      <CubeIcon class="h-3.5 w-3.5 text-[#a78bfa]" />
    </template>
    {{ def.name }}
    <template #count>
      <span class="text-[10px]">{{ def.type }}</span>
    </template>
  </SidebarRow>
</template>
```

Clicking a row puts `property:name=` into the search box with the cursor at the end, ready for the user to type a value.

---

## 8 — `bookmarkStore.updateProperties`

Add to `bookmark.ts`:

```ts
async function updateProperties(bookmarkId: string, values: Record<string, unknown>) {
  const updated = await bookmarkApi.apiBookmarksIdPropertiesPut({
    id: bookmarkId,
    body: values,
  })
  const idx = bookmarks.value.findIndex(b => b.id === bookmarkId)
  if (idx !== -1) bookmarks.value[idx] = updated
}
```

---

## Locale additions

```
property.sectionLabel: "Properties"
property.badgesToggle: "Show badges on cards"
property.badgesToggleDesc: "Render property values inline below each bookmark's tags"
property.sidebarToggle: "Show in sidebar"
property.sidebarToggleDesc: "List defined properties in the left sidebar for quick filtering"
property.manageSectionHint: "Use property:name=value in search to filter, or click a badge on any card."
property.noDefinitions: "No properties yet — add one below."
property.addProperty: "Add property"
property.editProperty: "Edit property"
property.saveProperties: "Save properties"
property.fieldName: "Name"
property.fieldType: "Type"
property.fieldOptions: "Options"
property.fieldOptionsHint: "(comma-separated)"
property.optionsPlaceholder: "draft, review, published"
property.namePlaceholder: "e.g. status, priority, due…"
property.clearValue: "Clear"
property.notSet: "— not set —"
sidebar.properties: "Properties"
collectionSettings.title: "Collection settings"
collectionSettings.tabDisplay: "Display"
collectionSettings.tabProperties: "Properties"
collectionSettings.sectionLayout: "Layout"
collectionSettings.sectionProperties: "Properties"
```

---

## Acceptance checklist

- [ ] `npm run generate-api` produces `PropertyDefinitionResourceApi` and `PropertyDefinitionJson`.
- [ ] `usePropertyStore` — `fetchForCollection`, `create`, `update`, `remove` all work; list refreshes optimistically.
- [ ] On collection load, `propertyStore.fetchForCollection` is called.
- [ ] Gear `⚙` icon appears at far-right of the sticky toolbar, after the Sort button and a 1 px divider.
- [ ] Settings modal opens on gear click; closes on backdrop click, ✕ button, or Escape.
- [ ] **Display tab** — Layout toggle changes card layout. Both property toggles persist to localStorage and take effect immediately.
- [ ] **Properties tab** — Add / edit / delete definitions. "Save properties" persists to backend; Cancel discards local changes.
- [ ] `BookmarkCard` shows a prop-row of `key = value` chips when "Show badges on cards" is on and the bookmark has values set.
- [ ] Clicking a badge toggles `property:key=value` in the search query.
- [ ] `EditBookmarkDialog` shows a Properties section below the tags row. Inputs match the property type. Values save via `PUT /bookmarks/{id}/properties`.
- [ ] Search tokenizer parses `property:key=value` / `property:key>N` / `property:key>=N` etc.
- [ ] `filteredBookmarks` filters correctly for `=`, `>`, `<`, `>=`, `<=` operators.
- [ ] Search active chip renders property tokens with cube icon + `key op value` label; remove (×) works.
- [ ] Sidebar shows Properties section when the toggle is on; clicking a row inserts `property:name=` into search.
- [ ] `npm run type-check` clean. No regressions in existing search / filter / sort behaviour.

---

## Out of scope

- Property values in the grouped layout's group headers.
- Bulk-setting property values across multiple bookmarks.
- Per-user vs per-collection property visibility (both toggles are global localStorage only for now).
- Server-side filtering by property (all filtering is client-side, consistent with the rest of the app).
- Property value history / audit log.
- System-managed / read-only properties (all properties are user-editable; see BR-075 removal in UC-068).
- Hard limit on the number of property definitions per collection (see BR-071 update in UC-067).

## Known defects

- **Properties not available in the Create Bookmark dialog.** `CreateBookmarkDialog.vue` does not show property inputs. Users must create the bookmark first, then edit it to set property values. Fix: add the same properties section from `EditBookmarkDialog.vue` into `CreateBookmarkDialog.vue` (possibly by extracting a shared `BookmarkPropertyFields.vue` component), and call `bookmarkStore.updateProperties()` after creation.
