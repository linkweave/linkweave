// Pure tri-state logic for the batch tag editor (UC-074a), extracted from
// BatchTagEditor.vue so the fiddly bits — the click cycle, the net-change
// computation (net-zero exclusion, k counts) and the toast assembly
// (max-k across tags, first-clause capitalization) — can be unit-tested
// without mounting the component.

export type Base = 'all' | 'some' | 'none'
export type Intent = 'add' | 'remove'
export type Mark = 'check' | 'dash' | 'empty' | 'empty-x'

/** Base state of a tag across a selection of `n` items it appears on `count` times. */
export function baseState(count: number, n: number): Base {
  if (count === 0) return 'none'
  if (count === n) return 'all'
  return 'some'
}

/**
 * Next draft intent for a click on a row, given its base state and current
 * intent (`undefined` = leave). Cycles:
 *   all  : leave → remove → leave
 *   none : leave → add → leave
 *   some : leave → add → remove → leave
 */
export function nextIntent(base: Base, current: Intent | undefined): Intent | undefined {
  if (base === 'all') return current === 'remove' ? undefined : 'remove'
  if (base === 'none') return current === 'add' ? undefined : 'add'
  // some
  if (current === undefined) return 'add'
  if (current === 'add') return 'remove'
  return undefined
}

/** The box glyph to render: a staged intent overrides the base state. */
export function markFor(base: Base, intent: Intent | undefined): Mark {
  if (intent === 'add') return 'check'
  if (intent === 'remove') return 'empty-x'
  if (base === 'all') return 'check'
  if (base === 'some') return 'dash'
  return 'empty'
}

/** `aria-checked` value mirroring the tri-state for assistive tech. */
export function ariaCheckedFor(base: Base, intent: Intent | undefined): 'true' | 'false' | 'mixed' {
  if (intent === 'add') return 'true'
  if (intent === 'remove') return 'false'
  if (base === 'all') return 'true'
  if (base === 'none') return 'false'
  return 'mixed'
}

export interface TagState {
  id: string
  name: string
  isNew: boolean
  /** How many of the selected bookmarks already carry this tag. */
  count: number
}

export interface ChangeItem {
  id: string
  name: string
  isNew: boolean
  /** Number of bookmarks actually affected by the change (non-zero). */
  k: number
}

/**
 * Net change set for a draft over a selection of size `n`. Zero-effect drafts
 * (adding a tag already on all, removing one on none) are dropped, so an empty
 * result means "nothing to apply". Order follows draft insertion order.
 */
export function computeChanges(
  draft: Record<string, Intent>,
  tags: TagState[],
  n: number,
): { adds: ChangeItem[]; removes: ChangeItem[] } {
  const byId = new Map(tags.map((t) => [t.id, t]))
  const adds: ChangeItem[] = []
  const removes: ChangeItem[] = []
  for (const [id, intent] of Object.entries(draft)) {
    const tag = byId.get(id)
    if (!tag) continue
    if (intent === 'add') {
      const k = n - tag.count
      if (k > 0) adds.push({ id, name: tag.name, isNew: tag.isNew, k })
    } else {
      const k = tag.count
      if (k > 0) removes.push({ id, name: tag.name, isNew: tag.isNew, k })
    }
  }
  return { adds, removes }
}

/** Compact footer summary, e.g. `+java  −red  +design` (empty when clean). */
export function changeSummary(adds: ChangeItem[], removes: ChangeItem[]): string {
  if (!adds.length && !removes.length) return ''
  return [...adds.map((a) => `+${a.name}`), ...removes.map((r) => `−${r.name}`)].join('  ')
}

export type ToastKey = 'toastAdded' | 'toastRemoved' | 'toastBoth'
export type ToastParams = Record<string, string | number>

/**
 * Net-change success toast. Picks one of three full-sentence locale keys —
 * adds-only, removes-only, or both — so each is a complete, translator-owned
 * sentence (no programmatic capitalization or lowercase-clause assumptions).
 * `k` is the MAX affected count across that clause's tags. `translate` returns
 * the localized, period-terminated string for the chosen key.
 */
export function buildToastMessage(
  adds: ChangeItem[],
  removes: ChangeItem[],
  translate: (key: ToastKey, params: ToastParams) => string,
): string {
  const quote = (items: ChangeItem[]) => items.map((i) => `"${i.name}"`).join(', ')
  const maxK = (items: ChangeItem[]) => Math.max(...items.map((i) => i.k))
  if (adds.length && removes.length) {
    return translate('toastBoth', {
      addNames: quote(adds),
      addK: maxK(adds),
      removeNames: quote(removes),
      removeK: maxK(removes),
    })
  }
  if (adds.length) return translate('toastAdded', { names: quote(adds), k: maxK(adds) })
  return translate('toastRemoved', { names: quote(removes), k: maxK(removes) })
}
