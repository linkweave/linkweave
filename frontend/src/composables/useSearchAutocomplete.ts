import { computed } from 'vue'
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
    const byName = new Map<string, string>() // definitionId -> lowercased name
    const sets = new Map<string, Set<string>>() // lowercased name -> values
    for (const def of propertyStore.definitions) {
      const name = def.data.name.toLowerCase()
      byName.set(def.id, name)
      const set = sets.get(name) ?? new Set<string>()
      for (const raw of (def.data.allowedValues ?? '').split(',')) {
        const v = raw.trim()
        if (v) set.add(v)
      }
      sets.set(name, set)
    }
    for (const b of bookmarkStore.bookmarks) {
      for (const pv of b.propertyValues ?? []) {
        const name = byName.get(pv.definitionId)
        if (!name) continue
        const set = sets.get(name) ?? new Set<string>()
        if (pv.valueText) {
          for (const part of pv.valueText.split(',')) {
            const v = part.trim()
            if (v) set.add(v)
          }
        } else if (pv.valueNumber !== undefined) set.add(String(pv.valueNumber))
        else if (pv.valueBoolean !== undefined) set.add(String(pv.valueBoolean))
        sets.set(name, set)
      }
    }
    const out: Record<string, string[]> = {}
    for (const [name, set] of sets) {
      out[name] = [...set].sort((a, b) => a.localeCompare(b))
    }
    return out
  })

  function parseQueryForAutoCompl(query: string, cursor: number): AcResult | null {
    const { token, colonToken, range } = tokenAtCursor(query, cursor)
    const tl = token.toLowerCase()
    const ctl = colonToken.toLowerCase()

    // ── # or tag: → Tags
    if (token.startsWith('#') || ctl.startsWith('tag:')) {
      const isHash = token.startsWith('#')
      const filter = (isHash ? token.slice(1) : colonToken.slice(4)).toLowerCase()
      const prefix = isHash ? '#' : 'tag:'
      const items: AcItem[] = allTags.value
        .filter((t) => !filter || t.name.toLowerCase().includes(filter))
        .map((t) => ({
          key: t.name,
          label: t.name,
          insert: prefix + t.name,
          type: 'tag',
          color: t.color,
          filter,
        }))
      return { mode: 'tag', label: 'tags', items, range }
    }

    // ── folder: / under: → Folders. Same source, different operator: `under:`
    // is hierarchical (matches subfolders), `folder:` is a flat name match.
    const folderOp = (['folder', 'under'] as const).find((op) => ctl.startsWith(op + ':'))
    if (folderOp) {
      const filter = colonToken.slice(folderOp.length + 1).toLowerCase()
      const items: AcItem[] = allFolders.value
        .filter((f) => !filter || f.toLowerCase().includes(filter))
        .map((f) => ({ key: f, label: f, insert: folderOp + ':' + f, type: folderOp, filter }))
      return { mode: folderOp, label: 'folders', items, range }
    }

    // ── property: → keys or values
    if (ctl.startsWith('property:')) {
      const rest = colonToken.slice(9)
      const eqIdx = rest.indexOf('=')
      if (eqIdx === -1) {
        const filter = rest.toLowerCase()
        const items: AcItem[] = allPropKeys.value
          .filter((k) => !filter || k.toLowerCase().includes(filter))
          .map((k) => ({
            key: k,
            label: k,
            insert: 'property:' + k + '=',
            type: 'prop-key',
            hint: 'hintValue',
            filter,
          }))
        return { mode: 'prop-key', label: 'properties', items, range }
      }
      const propKey = rest.slice(0, eqIdx)
      const valFilter = rest.slice(eqIdx + 1).toLowerCase()
      const vals = allPropVals.value[propKey.toLowerCase()] ?? []
      const items: AcItem[] = vals
        .filter((v) => !valFilter || v.toLowerCase().includes(valFilter))
        .map((v) => ({
          key: v,
          label: v,
          insert: 'property:' + propKey + '=' + v,
          type: 'prop-val',
          propKey,
          filter: valFilter,
        }))
      return { mode: 'prop-val', label: propKey, items, range }
    }

    // ── Operator discovery: "fo", "ta", "prop" …
    if (token.length >= 2 && !token.includes(':')) {
      const matched = OPS.filter((op) => op.trigger.startsWith(tl) && tl !== op.trigger)
      if (matched.length) {
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
    }

    return null
  }

  return { parseQueryForAutoCompl }
}
