import { computed } from 'vue'
import type { BookmarkPropertyValueJson } from '@/api/generated'
import { useTagStore } from '@/stores/tag'
import { useFolderStore } from '@/stores/folder'
import { usePropertyStore } from '@/stores/property'
import { useBookmarkStore } from '@/stores/bookmark'
import { OPS, tokenAtCursor } from './searchAutocompleteToken'

export type AcMode = 'tag' | 'folder' | 'under' | 'prop-key' | 'prop-val' | 'operator'

export interface AcItem {
  key: string
  label: string
  insert: string // full text to replace the current token with
  type: AcMode
  color?: string // tag swatch hex
  propKey?: string // for prop-val items — the key as typed
  filter: string // substring that was typed — used by the highlighter
  hint?: string // right-aligned secondary label — an i18n key under search.autocomplete
}

export interface AcResult {
  mode: AcMode
  label: string // dropdown header text (i18n key suffix below)
  items: AcItem[]
  range: [number, number] // [tokenStart, tokenEnd] — slice of query to replace
}

interface AcTag {
  name: string
  color?: string
}

/** Splits a comma-separated string into trimmed, non-empty values. */
function csvValues(raw: string | null | undefined): string[] {
  return (raw ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
}

/** The displayable string values carried by a single bookmark property value. */
function propertyValueStrings(pv: BookmarkPropertyValueJson): string[] {
  if (pv.valueText) return csvValues(pv.valueText)
  if (pv.valueNumber !== undefined) return [String(pv.valueNumber)]
  if (pv.valueBoolean !== undefined) return [String(pv.valueBoolean)]
  return []
}

type TokenCtx = ReturnType<typeof tokenAtCursor>

/** `#tag` / `tag:` → matching tags. */
function tagSuggestions({ token, colonToken, range }: TokenCtx, allTags: AcTag[]): AcResult | null {
  if (!token.startsWith('#') && !colonToken.toLowerCase().startsWith('tag:')) return null
  const isHash = token.startsWith('#')
  const filter = (isHash ? token.slice(1) : colonToken.slice(4)).toLowerCase()
  const prefix = isHash ? '#' : 'tag:'
  const items: AcItem[] = allTags
    .filter((t) => !filter || t.name.toLowerCase().includes(filter))
    .map((t) => ({ key: t.name, label: t.name, insert: prefix + t.name, type: 'tag', color: t.color, filter }))
  return { mode: 'tag', label: 'tags', items, range }
}

/** `folder:` (flat) / `under:` (hierarchical) → matching folders. */
function folderSuggestions({ colonToken, range }: TokenCtx, allFolders: string[]): AcResult | null {
  const ctl = colonToken.toLowerCase()
  const folderOp = (['folder', 'under'] as const).find((op) => ctl.startsWith(op + ':'))
  if (!folderOp) return null
  const filter = colonToken.slice(folderOp.length + 1).toLowerCase()
  const items: AcItem[] = allFolders
    .filter((f) => !filter || f.toLowerCase().includes(filter))
    .map((f) => ({ key: f, label: f, insert: folderOp + ':' + f, type: folderOp, filter }))
  return { mode: folderOp, label: 'folders', items, range }
}

/** `property:` → keys before `=`, values after. */
function propertySuggestions(
  { colonToken, range }: TokenCtx,
  allPropKeys: string[],
  allPropVals: Record<string, string[]>,
): AcResult | null {
  if (!colonToken.toLowerCase().startsWith('property:')) return null
  const rest = colonToken.slice(9)
  const eqIdx = rest.indexOf('=')
  if (eqIdx === -1) {
    const filter = rest.toLowerCase()
    const items: AcItem[] = allPropKeys
      .filter((k) => !filter || k.toLowerCase().includes(filter))
      .map((k) => ({ key: k, label: k, insert: 'property:' + k + '=', type: 'prop-key', hint: 'hintValue', filter }))
    return { mode: 'prop-key', label: 'properties', items, range }
  }
  const propKey = rest.slice(0, eqIdx)
  const valFilter = rest.slice(eqIdx + 1).toLowerCase()
  const vals = allPropVals[propKey.toLowerCase()] ?? []
  const items: AcItem[] = vals
    .filter((v) => !valFilter || v.toLowerCase().includes(valFilter))
    .map((v) => ({ key: v, label: v, insert: 'property:' + propKey + '=' + v, type: 'prop-val', propKey, filter: valFilter }))
  return { mode: 'prop-val', label: propKey, items, range }
}

/** A 2+ char bare word ("fo", "prop") → matching operator names. */
function operatorSuggestions({ token, range }: TokenCtx): AcResult | null {
  const tl = token.toLowerCase()
  if (token.length < 2 || token.includes(':')) return null
  const matched = OPS.filter((op) => op.trigger.startsWith(tl) && tl !== op.trigger)
  if (!matched.length) return null
  return {
    mode: 'operator',
    label: 'operators',
    items: matched.map((op) => ({
      key: op.full,
      label: op.full,
      insert: op.full,
      type: 'operator',
      hint: op.hintKey,
      filter: token,
    })),
    range,
  }
}

export function useSearchAutocomplete() {
  const tagStore = useTagStore()
  const folderStore = useFolderStore()
  const propertyStore = usePropertyStore()
  const bookmarkStore = useBookmarkStore()

  const allTags = computed<AcTag[]>(() =>
    tagStore.tags.map((t) => ({ name: t.data.name, color: t.data.color ?? undefined })),
  )

  const allFolders = computed<string[]>(() => {
    const names = new Set<string>()
    for (const f of folderStore.folders) names.add(f.data.name)
    return [...names].sort((a, b) => a.localeCompare(b))
  })

  const allPropKeys = computed<string[]>(() =>
    [...propertyStore.definitions]
      .map((d) => d.data.name)
      .sort((a, b) => a.localeCompare(b)),
  )

  // Map every property value seen in the collection to its definition's
  // (lowercased) name. Combines declared `allowedValues` with values actually
  // present on bookmarks so free-text properties also get suggestions.
  const allPropVals = computed<Record<string, string[]>>(() => {
    const nameByDefId = new Map<string, string>() // definitionId -> lowercased name
    const valuesByName = new Map<string, Set<string>>() // lowercased name -> values

    const collect = (name: string, values: string[]) => {
      const set = valuesByName.get(name) ?? new Set<string>()
      for (const v of values) set.add(v)
      valuesByName.set(name, set)
    }

    // Seed each property's value set from its declared allowedValues.
    for (const def of propertyStore.definitions) {
      const name = def.data.name.toLowerCase()
      nameByDefId.set(def.id, name)
      collect(name, csvValues(def.data.allowedValues))
    }

    // Augment with values actually present on bookmarks (covers free-text props).
    for (const b of bookmarkStore.bookmarks) {
      for (const pv of b.propertyValues ?? []) {
        const name = nameByDefId.get(pv.definitionId)
        if (name) collect(name, propertyValueStrings(pv))
      }
    }

    const out: Record<string, string[]> = {}
    for (const [name, set] of valuesByName) {
      out[name] = [...set].sort((a, b) => a.localeCompare(b))
    }
    return out
  })

  // Dispatch to the first mode whose trigger matches the token under the
  // cursor. `folder:`/`under:` is hierarchical vs flat; each builder owns the
  // shape of its own suggestions. Add a mode by writing a builder and slotting
  // it into this chain.
  function parseQueryForAutoCompl(query: string, cursor: number): AcResult | null {
    const ctx = tokenAtCursor(query, cursor)
    return (
      tagSuggestions(ctx, allTags.value) ??
      folderSuggestions(ctx, allFolders.value) ??
      propertySuggestions(ctx, allPropKeys.value, allPropVals.value) ??
      operatorSuggestions(ctx)
    )
  }

  return { parseQueryForAutoCompl }
}
