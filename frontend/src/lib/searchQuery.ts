// UC-070 search-query tokenizer + matcher.
// Implements: #tag, tag:, folder:, under:, url: (exact URL), note:, created:,
// property:, match: (the free-text combinator), free text, and negation (-).
// `compileQuery` resolves everything query-side once; the result matches many
// bookmarks without re-parsing a value per bookmark.
// The known-operator set lives in searchOperators.ts. An unquoted `key:value`
// whose key is outside that set is free text (`Bug:123`, `localhost:5173/x`,
// a pasted URL); an explicitly quoted `key:"value"` with an unknown key, or a
// known operator whose value does not parse (`url:`, `created:`, `property:`),
// is invalid syntax and matches nothing. Neither is ever a silent match-all
// (BR-070-2).

import { normalizeUrl, parseAbsoluteUrl } from './url'
import { isKnownOperator } from './searchOperators'
import { matchesCreated, parseCreatedValue } from './searchQueryCreated'
import { matchesPropertyToken, parsePropertyValue, type PropertyDef } from './searchQueryProperty'
// Re-export the operator-specific helpers so existing callers
// (`@/lib/searchQuery`) keep working — each grammar lives in its own file
// but is conceptually part of the search lib.
export { parseCreatedValue, matchesCreated } from './searchQueryCreated'
export type { DateOp, ParsedCreated } from './searchQueryCreated'
export { parsePropertyValue, matchesPropertyToken } from './searchQueryProperty'
export type { PropertyOp, ParsedProperty, PropertyDef } from './searchQueryProperty'

/**
 * The discriminant of a `QueryToken`. Named centrally so future kinds (groups
 * for OR, etc.) only need to be added in one place — and so search-related
 * code can refer to `TokenKind` instead of repeating string literals.
 */
export type TokenKind = 'tag' | 'operator' | 'text'

export type TagToken = { kind: 'tag'; value: string; neg: boolean }
export type OperatorToken = { kind: 'operator'; key: string; value: string; neg: boolean }
export type TextToken = { kind: 'text'; value: string; neg: boolean }
export type QueryToken = TagToken | OperatorToken | TextToken

export interface MatchContext {
  // Lowercased tag names looked up by id on the bookmark
  tagNamesById: Map<string, string>
  // Lowercased folder name resolved from the bookmark's folderId (or null)
  folderName: string | null
  // Lowercased names of every ancestor folder of the bookmark (incl. its own
  // folder), used to evaluate the hierarchical `under:` operator. Empty for
  // unfiled bookmarks.
  ancestorFolderNames: Set<string>
  // Folder IDs of every ancestor (incl. own folder). Used by `under:` when the
  // token value is a folder ID (the unambiguous click-path encoding); names
  // remain the fallback for typed queries.
  ancestorFolderIds: Set<string>
  // Property definitions in the active collection, keyed by lowercase name.
  // Optional so existing callers that don't enable property matching can omit.
  propertyDefsByName?: Map<string, PropertyDef>
}

export interface MatchableBookmark {
  data: {
    title?: string | null
    url?: string | null
    description?: string | null
    tagIds?: Set<string> | null
  }
  // Created-at timestamp, used by the `created:` operator (UC-070 BR-084/085).
  // Optional so callers that don't enable date matching can skip wiring it.
  entityInfo?: { timestampErstellt?: Date | null } | null
  // Per-bookmark property values, used by the `property:` operator
  // Optional for the same reason as above.
  propertyValues?: Array<{
    definitionId: string
    valueText?: string
    valueNumber?: number
    valueBoolean?: boolean
  }>
}

export interface AncestorSets {
  names: Set<string>
  ids: Set<string>
}

export const EMPTY_ANCESTORS: AncestorSets = { names: new Set(), ids: new Set() }

/**
 * walk the tree of folders up to the root, collecting the names and IDs of the folders
 * along the way. Then  an object with the names and IDs of the folders along the path
 * @param folderId
 * @param namesById
 * @param parentById
 */
export function buildAncestorSets(
  folderId: string,
  namesById: Map<string, string>,
  parentById: Map<string, string | null>,
): AncestorSets {
  const acc: AncestorSets = { names: new Set(), ids: new Set() }
  const visited = new Set<string>()
  let cur: string | null = folderId
  while (cur && !visited.has(cur)) {
    visited.add(cur)
    acc.ids.add(cur)
    const name = namesById.get(cur)
    if (name) acc.names.add(name)
    cur = parentById.get(cur) ?? null
  }
  return acc
}

// Match: -? ( #"q" | #'q' | #word | key:"q" | key:'q' | key:word | "q" | 'q' | word )
// Both ASCII quote flavors are accepted; preserving single-quote support keeps
// older saved queries (and the `utils/search.ts` ergonomics that predated this
// tokenizer) working. The output token never re-quotes — `stringifyTokens`
// always emits double-quoted form.
const TOKEN_RE =
  /(-)?(?:#"((?:[^"\\]|\\.)*)"|#'([^']*)'|#([\w-]+)|([a-z]+):"((?:[^"\\]|\\.)*)"|([a-z]+):'([^']*)'|([a-z]+):(\S+)|"((?:[^"\\]|\\.)*)"|'([^']*)'|(\S+))/gi

// Inside the double-quoted form a `\` escapes the next character, so a value
// may carry a literal `"` (common in pasted URLs). Inverse of `quoteIfNeeded`;
// the two must always be changed together or `stringifyTokens` -> `tokenize`
// stops round-tripping and every pill-remove click rewrites the query.
function unescapeQuoted(v: string): string {
  return v.replace(/\\(.)/g, '$1')
}

export function tokenize(query: string): QueryToken[] {
  const tokens: QueryToken[] = []
  if (!query) return tokens
  TOKEN_RE.lastIndex = 0
  let m: RegExpExecArray | null
  while ((m = TOKEN_RE.exec(query)) !== null) {
    const neg = !!m[1]
    const tagDq = m[2] === undefined ? undefined : unescapeQuoted(m[2])
    const tagSq = m[3]
    const tagWord = m[4]
    const opKeyDq = m[5]
    const opValDq = m[6] === undefined ? undefined : unescapeQuoted(m[6])
    const opKeySq = m[7]
    const opValSq = m[8]
    const opKey = m[9]
    const opVal = m[10]
    const textDq = m[11] === undefined ? undefined : unescapeQuoted(m[11])
    const textSq = m[12]
    const textW = m[13]

    const tagValue = tagDq ?? tagSq ?? tagWord
    if (tagValue !== undefined) {
      tokens.push({ kind: 'tag', value: tagValue, neg })
      continue
    }
    if (opKeyDq !== undefined && opValDq !== undefined) {
      tokens.push({ kind: 'operator', key: opKeyDq.toLowerCase(), value: opValDq, neg })
      continue
    }
    if (opKeySq !== undefined && opValSq !== undefined) {
      tokens.push({ kind: 'operator', key: opKeySq.toLowerCase(), value: opValSq, neg })
      continue
    }
    if (opKey !== undefined && opVal !== undefined) {
      // An unquoted `key:value` whose key is outside the known operator set is
      // ordinary free text that happens to contain a colon: `Bug:123`,
      // `localhost:5173/x`, `FOO:bar`, and every pasted URL (which parses
      // as key `https`). BR-070-2 allows either remedy for an unknown key —
      // free text or an invalid-syntax flag — and free text is the one that
      // keeps such a query working instead of voiding it.
      //
      // The quoted forms above deliberately stay operators: writing
      // `bogus:"x y"` is explicit operator shape, so it earns the A2 flag
      // rather than a silent literal search. Known keys also stay operators,
      // which is what keeps `note://internal` a note search (and keeps
      // `folder:"//shared"` round-tripping through `stringifyTokens`).
      if (!isKnownOperator(opKey)) {
        tokens.push({ kind: 'text', value: `${opKey}:${opVal}`, neg })
      } else {
        tokens.push({ kind: 'operator', key: opKey.toLowerCase(), value: opVal, neg })
      }
      continue
    }
    const textValue = textDq ?? textSq ?? textW
    if (textValue !== undefined) {
      tokens.push({ kind: 'text', value: textValue, neg })
    }
  }
  return tokens
}

// Whitespace or `"` force the quoted form for every kind. Tags additionally
// require quoting whenever the value contains anything outside `[\w-]`, since
// the unquoted tag form (`#name`) only consumes `[\w-]+` — otherwise a tag
// like `foo=bar` would re-tokenize as tag `foo` + text `=bar`.
const TAG_UNQUOTED_RE = /^[\w-]+$/

// A free-text value that starts with a known operator key plus a colon would
// re-tokenize as that operator, silently changing what the query means the
// first time a pill is removed. Unknown keys are safe: they tokenize back to
// text (BR-070-2), which is exactly what they came from.
function textWouldReparseAsOperator(value: string): boolean {
  const key = /^([a-z]+):/i.exec(value)?.[1]
  return key !== undefined && isKnownOperator(key)
}

// Same hazard, one character in: a value whose first character is itself
// grammar re-tokenizes as something else entirely. `-x` comes back as a
// *negated* token (an inverted filter), `#x` as a tag, and `'x'` as a
// single-quoted value with the quotes eaten — in every kind that can carry
// one. `"` is already covered above; `#` and `-` only lead the grammar for
// free text (`folder:#x` and `folder:-x` are ordinary operator values).
const LEADING_SYNTAX_RE: Record<TokenKind, RegExp> = {
  tag: /^'/,
  operator: /^'/,
  text: /^[-#']/,
}

function needsQuoting(value: string, kind: TokenKind): boolean {
  if (value.length === 0) return true
  if (/[\s"]/.test(value)) return true
  if (LEADING_SYNTAX_RE[kind].test(value)) return true
  if (kind === 'tag' && !TAG_UNQUOTED_RE.test(value)) return true
  if (kind === 'text' && textWouldReparseAsOperator(value)) return true
  return false
}

function quoteIfNeeded(value: string, kind: TokenKind): string {
  if (!needsQuoting(value, kind)) return value
  // Escape `\` and `"` so a value containing a quote survives the round trip
  // instead of splitting into two tokens. Inverse of `unescapeQuoted`.
  return `"${value.replace(/[\\"]/g, '\\$&')}"`
}

function tokenToString(t: QueryToken): string {
  const prefix = t.neg ? '-' : ''
  if (t.kind === 'tag') return `${prefix}#${quoteIfNeeded(t.value, 'tag')}`
  if (t.kind === 'operator') return `${prefix}${t.key}:${quoteIfNeeded(t.value, 'operator')}`
  return `${prefix}${quoteIfNeeded(t.value, 'text')}`
}

export function stringifyTokens(tokens: QueryToken[]): string {
  return tokens.map(tokenToString).join(' ')
}

function tokenKey(t: QueryToken): string {
  const key = t.kind === 'operator' ? t.key : ''
  return `${t.kind}|${key}|${t.value.toLowerCase()}`
}

function sameValue(a: QueryToken, b: QueryToken): boolean {
  return tokenKey(a) === tokenKey(b)
}

export function toggleToken(
  tokens: QueryToken[],
  token: QueryToken,
  modifier?: 'exclude',
): QueryToken[] {
  const without = tokens.filter(x => !sameValue(x, token))
  if (modifier === 'exclude') {
    return [...without, { ...token, neg: true }]
  }
  // Plain toggle: if any token with the same kind/key/value exists (regardless of neg), remove it.
  if (without.length !== tokens.length) return without
  return [...tokens, { ...token, neg: false }]
}

// Exact (case-insensitive) tag-name match. Shared by the `#name` (tag kind) and
// `tag:name` (operator alias) forms so they always behave identically.
function bookmarkHasTagNamed(b: MatchableBookmark, value: string, ctx: MatchContext): boolean {
  if (!b.data.tagIds) return false
  const target = value.toLowerCase()
  for (const tagId of b.data.tagIds) {
    const name = ctx.tagNamesById.get(tagId)
    if (name && name === target) return true
  }
  return false
}

// Free text: match against title + url + description + tag names. `v` is the
// already-lowercased token value.
function bookmarkMatchesText(b: MatchableBookmark, v: string, ctx: MatchContext): boolean {
  if (b.data.title?.toLowerCase().includes(v)) return true
  if (b.data.url?.toLowerCase().includes(v)) return true
  if (b.data.description?.toLowerCase().includes(v)) return true
  if (b.data.tagIds) {
    for (const tagId of b.data.tagIds) {
      const name = ctx.tagNamesById.get(tagId)
      if (name?.includes(v)) return true
    }
  }
  return false
}

// ── Compilation ────────────────────────────────────────────────────────────
// A token's predicate is built once per query, never once per bookmark:
// parsing a `url:` / `created:` / `property:` value is pure query-side work,
// and doing it inside the per-bookmark loop made a collection of N bookmarks
// re-parse the same value N times on every keystroke.

/** A prepared, query-side-resolved test for one token. */
type Predicate = (b: MatchableBookmark, ctx: MatchContext) => boolean

// A value that does not parse is invalid syntax and matches nothing.
// `isInvalidToken` reports exactly the same tokens, so the flag in the search
// bar and the results can never disagree.
const NEVER: Predicate = () => false

function prepareCreated(value: string): Predicate {
  const parsed = parseCreatedValue(value)
  if (!parsed) return NEVER
  return (b) => {
    const createdAt = b.entityInfo?.timestampErstellt
    if (!createdAt) return false // a bookmark with no timestamp can't satisfy a date filter
    return matchesCreated(createdAt instanceof Date ? createdAt : new Date(createdAt), parsed)
  }
}

function prepareProperty(value: string): Predicate {
  const parsed = parsePropertyValue(value)
  if (!parsed) return NEVER
  // No `propertyDefsByName` = the collection's definitions aren't wired in.
  return (b, ctx) =>
    !!ctx.propertyDefsByName &&
    matchesPropertyToken(b.propertyValues, ctx.propertyDefsByName, parsed)
}

// `url:` exact match: both sides are compared in their normalized forms via
// the shared `normalizeUrl` contract (lowercased scheme and host, trailing
// slashes stripped, query parameters sorted, fragment dropped, tracking
// parameters kept) — the same normalization used for duplicate detection
// (UC-096 BR-080). A value that does not parse as an absolute URL (the same
// `parseAbsoluteUrl` rule `isInvalidToken` applies, so the flag and the
// matcher can never disagree) is invalid syntax and matches nothing. The
// value is used verbatim, not lowercased: path and query compare
// case-sensitively. Non-hierarchical schemes the app can store via API or
// import (`mailto:…`) compare through the same normalization.
function prepareUrl(value: string): Predicate {
  if (!parseAbsoluteUrl(value)) return NEVER
  const target = normalizeUrl(value)
  return (b) => !!b.data.url && normalizeUrl(b.data.url) === target
}

/**
 * The free-text combinator (UC-070 BR-081). `match:` is not a filter — it is a
 * query-level setting that says how the *free-text* terms combine. Operators
 * always AND, whatever the mode. Exported so the autocomplete offers the same
 * two modes the matcher accepts.
 */
export const MATCH_MODES = ['and', 'or'] as const

function isMatchMode(value: string): boolean {
  return (MATCH_MODES as readonly string[]).includes(value.toLowerCase())
}

function isMatchModeToken(t: QueryToken): t is OperatorToken {
  return t.kind === 'operator' && t.key.toLowerCase() === 'match'
}

function prepareOperator(t: OperatorToken): Predicate {
  const v = t.value.toLowerCase()
  // Lowercased to match `isKnownOperator`, which is case-insensitive. Tokens
  // built by `tokenize` are already lowercase, but programmatic builders
  // (folder.ts, BookmarkCard.vue) are not forced to be — and a key that
  // `isInvalidToken` accepts must never fall through to `default` here, or a
  // negated token would flip that miss into an unfiltered list. `match:` never
  // reaches this switch: `compileQuery` consumes it as a setting.
  switch (t.key.toLowerCase()) {
    case 'tag': // `tag:name` is an alias for `#name`.
      return (b, ctx) => bookmarkHasTagNamed(b, t.value, ctx)
    case 'folder':
      return (_b, ctx) => (ctx.folderName ?? '').includes(v)
    case 'under':
      // Hierarchical: matches when the bookmark's folder or any ancestor is the
      // one referenced by `value`. Exact folder-ID match first (case-sensitive,
      // the click-path encoding from selectFolder), then a case-insensitive name
      // fallback for typed queries (which keeps duplicate-name ambiguity by design).
      return (_b, ctx) => ctx.ancestorFolderIds.has(t.value) || ctx.ancestorFolderNames.has(v)
    case 'url':
      return prepareUrl(t.value)
    case 'note':
      return (b) => (b.data.description ?? '').toLowerCase().includes(v)
    case 'created':
      return prepareCreated(t.value)
    case 'property':
      return prepareProperty(t.value)
    default:
      // Unknown operator key: invalid syntax, matches nothing — a match-all
      // fallback would turn a typo into a silently unfiltered list.
      return NEVER
  }
}

function prepareToken(t: QueryToken): Predicate {
  switch (t.kind) {
    case 'tag':
      return (b, ctx) => bookmarkHasTagNamed(b, t.value, ctx)
    case 'operator':
      return prepareOperator(t)
    default: {
      const v = t.value.toLowerCase()
      return (b, ctx) => bookmarkMatchesText(b, v, ctx)
    }
  }
}

export interface CompiledQuery {
  /**
   * The query contains invalid syntax, so it matches nothing at all (A2) and
   * callers may skip the per-bookmark pass entirely. `matches` enforces this
   * on its own, so honouring the flag is an optimisation, never a correctness
   * requirement.
   */
  invalid: boolean
  matches: (b: MatchableBookmark, ctx: MatchContext) => boolean
}

const MATCHES_NOTHING: CompiledQuery = { invalid: true, matches: () => false }

/**
 * Turn a token list into a matcher. Everything that depends only on the query
 * — validity, the free-text combinator, and each token's parsed value — is
 * resolved here once, leaving the per-bookmark path pure comparison.
 */
export function compileQuery(tokens: QueryToken[]): CompiledQuery {
  // Invalid syntax matches nothing — negation must not flip it back to a
  // vacuous match-all (`-bogus:x` showing the entire collection is the
  // same silent-unfiltered failure mode as `bogus:x` matching everything).
  if (tokens.some(isInvalidToken)) return MATCHES_NOTHING

  // The mode is a property of the whole query, so `match:` governs terms that
  // precede it just as much as ones that follow; the last one wins, like any
  // other setting.
  let orMode = false
  for (const t of tokens) {
    if (isMatchModeToken(t)) orMode = t.value.toLowerCase() === 'or'
  }

  // Everything except positive free text ANDs. In OR mode the positive
  // free-text terms are satisfied by any one of them. Exclusions stay
  // unconditional even in OR mode: "-draft" means the bookmark must not
  // contain "draft", never "…or it may, as long as another term hit".
  const required: Predicate[] = []
  const orText: Predicate[] = []
  for (const t of tokens) {
    if (isMatchModeToken(t)) continue // a setting, not a filter
    const p = prepareToken(t)
    if (orMode && t.kind === 'text' && !t.neg) orText.push(p)
    else if (t.neg) required.push((b, ctx) => !p(b, ctx))
    else required.push(p)
  }

  return {
    invalid: false,
    matches(b, ctx) {
      for (const p of required) {
        if (!p(b, ctx)) return false
      }
      // `#java match:OR` has no free-text terms to combine, so the mode
      // constrains nothing — an empty OR is no filter, not a filter that
      // matches nothing.
      if (orText.length === 0) return true
      for (const p of orText) {
        if (p(b, ctx)) return true
      }
      return false
    },
  }
}

/**
 * Match a single bookmark. Convenience over `compileQuery` for one-shot
 * callers; a loop over many bookmarks must compile once and reuse the result.
 */
export function matchesTokens(
  b: MatchableBookmark,
  tokens: QueryToken[],
  ctx: MatchContext,
): boolean {
  return compileQuery(tokens).matches(b, ctx)
}

export function isInvalidToken(t: QueryToken): boolean {
  if (t.kind !== 'operator') return false
  const key = t.key.toLowerCase()
  if (!isKnownOperator(key)) return true
  if (key === 'url') return parseAbsoluteUrl(t.value) === null
  if (key === 'created') return parseCreatedValue(t.value) === null
  if (key === 'property') return parsePropertyValue(t.value) === null
  // `match:` takes exactly `and` or `or`. Negating a mode is meaningless, so
  // `-match:or` is flagged rather than guessed at.
  if (key === 'match') return t.neg || !isMatchMode(t.value)
  return false
}
