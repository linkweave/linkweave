// Single source of truth for the known search-operator set (UC-070
// BR-070-1). Consumed by the matcher (`searchQuery.ts`), the tokenizer's
// unknown-key rule (BR-070-2), the autocomplete (`searchAutocompleteToken.ts`
// derives `OPS` from it), and the invalid-syntax flag (`isInvalidToken`).
//
// Adding an operator means: a row here, a `case` in `prepareOperator`, and —
// until they too derive from this table — three presentation lists that still
// enumerate operators by hand:
//   • `OPERATOR_VARIANTS` in `components/bookmark/FilterPill.vue` (pill icon + label)
//   • the footer legend in `SearchAutocompleteDropdown.vue`
//   • the operator list spelled out in `search.invalidTokenHint` (en/de/fr)
// An operator added only here renders as a pill with no icon and is missing
// from the syntax help. (That component's `modeIcon` map and prefix chain are
// keyed by suggestion *mode*, not by operator, so they only need touching when
// an operator brings a new kind of suggestion list with it.)

/**
 * Operator whose key is offered when the user types a prefix of it. The
 * requirement is the hint string the dropdown renders beside the key — not
 * value suggestions behind the colon, which some discoverable operators have
 * (`folder:`, `property:`, `match:`) and others don't (`url:`).
 */
interface DiscoverableOperatorDef {
  /** Operator key as parsed (lowercase; the `key` of `key:value`). */
  key: string
  discoverable: true
  /** i18n key under `search.autocomplete` shown as the discovery hint. */
  hintKey: string
}

/**
 * Operator that is parsed and matched but never surfaced by prefix discovery,
 * because it has no hint string to show. `note:` and `created:` are free-typed
 * this way. Making one discoverable means adding a `hintKey` and its en/de/fr
 * strings — nothing else.
 */
interface PlainOperatorDef {
  key: string
  discoverable?: false
  hintKey?: never
}

export type OperatorDef = DiscoverableOperatorDef | PlainOperatorDef

export const OPERATOR_DEFS: readonly OperatorDef[] = [
  { key: 'tag', hintKey: 'opTag', discoverable: true },
  { key: 'folder', hintKey: 'opFolder', discoverable: true },
  { key: 'under', hintKey: 'opUnder', discoverable: true },
  { key: 'url', hintKey: 'opUrl', discoverable: true },
  { key: 'note' },
  { key: 'created' },
  { key: 'property', hintKey: 'opProperty', discoverable: true },
  { key: 'match', hintKey: 'opMatch', discoverable: true },
]

const KNOWN_KEYS: ReadonlySet<string> = new Set(OPERATOR_DEFS.map((d) => d.key))

export function isKnownOperator(key: string): boolean {
  return KNOWN_KEYS.has(key.toLowerCase())
}
