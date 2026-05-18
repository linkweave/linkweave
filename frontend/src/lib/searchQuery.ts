// UC-070 search-query tokenizer + matcher.
// Implements: #tag, folder:value, under:value, note:value, created:value,
// free text, and negation (-).
// Out of scope (parsed but no-op match-all): property:, match:OR.

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
const TOKEN_RE = /(-)?(?:#"([^"]*)"|#'([^']*)'|#([\w-]+)|([a-z]+):"([^"]*)"|([a-z]+):'([^']*)'|([a-z]+):(\S+)|"([^"]*)"|'([^']*)'|(\S+))/gi

export function tokenize(query: string): QueryToken[] {
  const tokens: QueryToken[] = []
  if (!query) return tokens
  TOKEN_RE.lastIndex = 0
  let m: RegExpExecArray | null
  while ((m = TOKEN_RE.exec(query)) !== null) {
    const neg = !!m[1]
    const tagDq = m[2]
    const tagSq = m[3]
    const tagWord = m[4]
    const opKeyDq = m[5]
    const opValDq = m[6]
    const opKeySq = m[7]
    const opValSq = m[8]
    const opKey = m[9]
    const opVal = m[10]
    const textDq = m[11]
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
      tokens.push({ kind: 'operator', key: opKey.toLowerCase(), value: opVal, neg })
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

function needsQuoting(value: string, kind: TokenKind): boolean {
  if (value.length === 0) return true
  if (/[\s"]/.test(value)) return true
  if (kind === 'tag' && !TAG_UNQUOTED_RE.test(value)) return true
  return false
}

function quoteIfNeeded(value: string, kind: TokenKind): string {
  return needsQuoting(value, kind) ? `"${value}"` : value
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

function bookmarkMatchesToken(b: MatchableBookmark, t: QueryToken, ctx: MatchContext): boolean {
  if (t.kind === 'tag') {
    const target = t.value.toLowerCase()
    if (!b.data.tagIds) return false
    for (const tagId of b.data.tagIds) {
      const name = ctx.tagNamesById.get(tagId)
      if (name && name === target) return true
    }
    return false
  }
  if (t.kind === 'operator') {
    const v = t.value.toLowerCase()
    if (t.key === 'folder') {
      return (ctx.folderName ?? '').includes(v)
    }
    if (t.key === 'under') {
      // Hierarchical: matches when the bookmark's own folder, or any ancestor,
      // is the one referenced by `value`. We try an exact folder-ID match
      // first (case-sensitive, the click-path encoding from selectFolder), and
      // fall back to a case-insensitive name match for typed queries. The name
      // fallback retains the duplicate-name ambiguity by design.
      if (ctx.ancestorFolderIds.has(t.value)) return true
      return ctx.ancestorFolderNames.has(v)
    }
    if (t.key === 'note') {
      return (b.data.description ?? '').toLowerCase().includes(v)
    }
    if (t.key === 'created') {
      const parsed = parseCreatedValue(t.value)
      if (!parsed) return true // unparseable → no-op match-all
      const createdAt = b.entityInfo?.timestampErstellt
      if (!createdAt) return false // bookmark with no timestamp can't satisfy a date filter
      return matchesCreated(createdAt instanceof Date ? createdAt : new Date(createdAt), parsed)
    }
    if (t.key === 'property') {
      const parsed = parsePropertyValue(t.value)
      if (!parsed) return true // unparseable → no-op match-all
      if (!ctx.propertyDefsByName) return false // collection has no definitions wired in
      return matchesPropertyToken(b.propertyValues, ctx.propertyDefsByName, parsed)
    }
    return true
  }
  // Free text: match against title + url + description + tag names
  const v = t.value.toLowerCase()
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

export function matchesTokens(
  b: MatchableBookmark,
  tokens: QueryToken[],
  ctx: MatchContext,
): boolean {
  for (const t of tokens) {
    let ok = bookmarkMatchesToken(b, t, ctx)
    if (t.neg) ok = !ok
    if (!ok) return false
  }
  return true
}
