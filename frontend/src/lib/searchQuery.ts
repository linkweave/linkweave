// UC-070 search-query tokenizer + matcher (lite subset).
// Implements: #tag, folder:value, note:value, free text, and negation (-).
// Out of scope (parsed but no-op match-all): property:, created:, match:OR.

export type TagToken = { kind: 'tag'; value: string; neg: boolean }
export type OpToken = { kind: 'op'; key: string; value: string; neg: boolean }
export type TextToken = { kind: 'text'; value: string; neg: boolean }
export type QueryToken = TagToken | OpToken | TextToken

export interface MatchContext {
  // Lowercased tag names looked up by id on the bookmark
  tagNamesById: Map<string, string>
  // Lowercased folder name resolved from the bookmark's folderId (or null)
  folderName: string | null
}

export interface MatchableBookmark {
  data: {
    title?: string | null
    url?: string | null
    description?: string | null
    tagIds?: Set<string> | null
  }
}

// Match: -? (#"quoted" | #word | key:"quoted" | key:word | "quoted" | word)
const TOKEN_RE = /(-)?(?:#"([^"]*)"|#([\w-]+)|([a-z]+):"([^"]*)"|([a-z]+):(\S+)|"([^"]*)"|(\S+))/gi

export function tokenize(query: string): QueryToken[] {
  const tokens: QueryToken[] = []
  if (!query) return tokens
  TOKEN_RE.lastIndex = 0
  let m: RegExpExecArray | null
  while ((m = TOKEN_RE.exec(query)) !== null) {
    const neg = !!m[1]
    const tagQuoted = m[2]
    const tagWord = m[3]
    const opKeyQ = m[4]
    const opValQ = m[5]
    const opKey = m[6]
    const opVal = m[7]
    const textQ = m[8]
    const textW = m[9]

    if (tagQuoted !== undefined || tagWord !== undefined) {
      tokens.push({ kind: 'tag', value: tagQuoted ?? tagWord!, neg })
    } else if (opKeyQ !== undefined && opValQ !== undefined) {
      tokens.push({ kind: 'op', key: opKeyQ.toLowerCase(), value: opValQ, neg })
    } else if (opKey !== undefined && opVal !== undefined) {
      tokens.push({ kind: 'op', key: opKey.toLowerCase(), value: opVal, neg })
    } else if (textQ !== undefined) {
      tokens.push({ kind: 'text', value: textQ, neg })
    } else if (textW !== undefined) {
      tokens.push({ kind: 'text', value: textW, neg })
    }
  }
  return tokens
}

// Whitespace or `"` force the quoted form for every kind. Tags additionally
// require quoting whenever the value contains anything outside `[\w-]`, since
// the unquoted tag form (`#name`) only consumes `[\w-]+` — otherwise a tag
// like `foo=bar` would re-tokenize as tag `foo` + text `=bar`.
const TAG_UNQUOTED_RE = /^[\w-]+$/

function needsQuoting(value: string, kind: QueryToken['kind']): boolean {
  if (value.length === 0) return true
  if (/[\s"]/.test(value)) return true
  if (kind === 'tag' && !TAG_UNQUOTED_RE.test(value)) return true
  return false
}

function quoteIfNeeded(value: string, kind: QueryToken['kind']): string {
  return needsQuoting(value, kind) ? `"${value}"` : value
}

function tokenToString(t: QueryToken): string {
  const prefix = t.neg ? '-' : ''
  if (t.kind === 'tag') return `${prefix}#${quoteIfNeeded(t.value, 'tag')}`
  if (t.kind === 'op') return `${prefix}${t.key}:${quoteIfNeeded(t.value, 'op')}`
  return `${prefix}${quoteIfNeeded(t.value, 'text')}`
}

export function stringifyTokens(tokens: QueryToken[]): string {
  return tokens.map(tokenToString).join(' ')
}

function tokenKey(t: QueryToken): string {
  const key = t.kind === 'op' ? t.key : ''
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
  if (t.kind === 'op') {
    const v = t.value.toLowerCase()
    if (t.key === 'folder') {
      return (ctx.folderName ?? '').includes(v)
    }
    if (t.key === 'note') {
      return (b.data.description ?? '').toLowerCase().includes(v)
    }
    // TODO UC-070: property:, created:, created:>, created:< — parse but match-all for now.
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
