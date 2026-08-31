// Single source of truth for the known search-operator set (UC-070
// BR-070-1). Consumed by the matcher (`searchQuery.ts`), the autocomplete
// (`searchAutocompleteToken.ts` derives `OPS` from it), and the
// invalid-syntax highlighting (`isInvalidToken` / SearchBar). Adding an
// operator = one row here plus its matcher case — nowhere else.

export interface OperatorDef {
  /** Operator key as parsed (lowercase; the `key` of `key:value`). */
  key: string
  /** i18n key under `search.autocomplete` shown as the discovery hint. */
  hintKey?: string
  /** Whether typed prefixes (e.g. `fo`) discover this operator. Operators
   * whose values are free-typed without suggestions stay `false`. */
  discoverable?: boolean
}

export const OPERATOR_DEFS: readonly OperatorDef[] = [
  { key: 'tag', hintKey: 'opTag', discoverable: true },
  { key: 'folder', hintKey: 'opFolder', discoverable: true },
  { key: 'under', hintKey: 'opUnder', discoverable: true },
  { key: 'url', hintKey: 'opUrl', discoverable: true },
  { key: 'note' },
  { key: 'created' },
  { key: 'property', hintKey: 'opProperty', discoverable: true },
]

const KNOWN_KEYS: ReadonlySet<string> = new Set(OPERATOR_DEFS.map((d) => d.key))

export function isKnownOperator(key: string): boolean {
  return KNOWN_KEYS.has(key.toLowerCase())
}
