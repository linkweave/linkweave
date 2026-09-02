import { describe, expect, it } from 'vitest'
import { isKnownOperator, KNOWN_OPERATORS_HINT, OPERATOR_DEFS } from './searchOperators'
import {
  buildAncestorSets,
  compileQuery,
  isInvalidToken,
  type MatchableBookmark,
  type MatchContext,
  matchesTokens,
  type QueryToken,
  stringifyTokens,
  toggleToken,
  tokenize,
} from './searchQuery'

describe('tokenize', () => {
  it('returns [] for empty input', () => {
    expect(tokenize('')).toEqual([])
  })

  it('parses a single free-text token', () => {
    expect(tokenize('hello')).toEqual([{ kind: 'text', value: 'hello', neg: false }])
  })

  it('parses a tag token', () => {
    expect(tokenize('#tag')).toEqual([{ kind: 'tag', value: 'tag', neg: false }])
  })

  it('parses a negated tag token', () => {
    expect(tokenize('-#tag')).toEqual([{ kind: 'tag', value: 'tag', neg: true }])
  })

  it('parses a quoted tag value with spaces', () => {
    expect(tokenize('#"two words"')).toEqual([{ kind: 'tag', value: 'two words', neg: false }])
  })

  it('parses an op token with quoted value', () => {
    expect(tokenize('folder:"multi word"')).toEqual([
      { kind: 'operator', key: 'folder', value: 'multi word', neg: false },
    ])
  })

  it('parses a mix of tokens', () => {
    expect(tokenize('#a folder:b -c')).toEqual([
      { kind: 'tag', value: 'a', neg: false },
      { kind: 'operator', key: 'folder', value: 'b', neg: false },
      { kind: 'text', value: 'c', neg: true },
    ])
  })

  it('accepts single-quoted phrases for backwards compat', () => {
    expect(tokenize("'two words'")).toEqual([{ kind: 'text', value: 'two words', neg: false }])
    expect(tokenize("#'two words'")).toEqual([{ kind: 'tag', value: 'two words', neg: false }])
    expect(tokenize("folder:'two words'")).toEqual([
      { kind: 'operator', key: 'folder', value: 'two words', neg: false },
    ])
  })

  it('does not crash on unknown operators', () => {
    expect(tokenize('property:foo created:>today')).toEqual([
      { kind: 'operator', key: 'property', value: 'foo', neg: false },
      { kind: 'operator', key: 'created', value: '>today', neg: false },
    ])
  })

  it('tokenizes a pasted absolute URL as a single free-text term', () => {
    expect(tokenize('https://example.com/a')).toEqual([
      { kind: 'text', value: 'https://example.com/a', neg: false },
    ])
    expect(tokenize('http://example.com/a?b=1')).toEqual([
      { kind: 'text', value: 'http://example.com/a?b=1', neg: false },
    ])
  })

  it('tokenizes an uppercase-scheme URL as free text, preserving its value', () => {
    expect(tokenize('HTTPS://Example.COM/a')).toEqual([
      { kind: 'text', value: 'HTTPS://Example.COM/a', neg: false },
    ])
  })

  it('tokenizes a negated pasted URL as a negated free-text term', () => {
    expect(tokenize('-https://example.com/a')).toEqual([
      { kind: 'text', value: 'https://example.com/a', neg: true },
    ])
  })

  it('tokenizes url: with an absolute URL value as one operator token', () => {
    expect(tokenize('url:https://example.com/a')).toEqual([
      { kind: 'operator', key: 'url', value: 'https://example.com/a', neg: false },
    ])
    expect(tokenize('-url:https://example.com/a')).toEqual([
      { kind: 'operator', key: 'url', value: 'https://example.com/a', neg: true },
    ])
  })

  it('tokenizes a quoted url: value with spaces as one operator token', () => {
    expect(tokenize('url:"https://example.com/a b"')).toEqual([
      { kind: 'operator', key: 'url', value: 'https://example.com/a b', neg: false },
    ])
  })

  it('treats an unquoted unknown key as free text, not an operator (BR-070-2)', () => {
    // The colon is ordinary punctuation in a search term far more often than it
    // is operator syntax, so `Bug:123` must keep finding `Bug:123`.
    expect(tokenize('bogus:value')).toEqual([{ kind: 'text', value: 'bogus:value', neg: false }])
    expect(tokenize('Bug:123')).toEqual([{ kind: 'text', value: 'Bug:123', neg: false }])
    expect(tokenize('localhost:5173/x')).toEqual([
      { kind: 'text', value: 'localhost:5173/x', neg: false },
    ])
    expect(tokenize('-TODO:refactor')).toEqual([
      { kind: 'text', value: 'TODO:refactor', neg: true },
    ])
  })

  it('keeps an explicitly quoted unknown key an operator, so A2 can flag it', () => {
    // Quoting is deliberate operator shape — nobody types `bogus:"x y"` meaning
    // a literal string — so it stays an operator and earns the invalid flag.
    expect(tokenize('bogus:"x y"')).toEqual([
      { kind: 'operator', key: 'bogus', value: 'x y', neg: false },
    ])
  })

  it('keeps a known key an operator even when its value starts with //', () => {
    // `note://internal` is a note search for `//internal`, not a free-text term.
    expect(tokenize('note://internal')).toEqual([
      { kind: 'operator', key: 'note', value: '//internal', neg: false },
    ])
    expect(tokenize('folder://shared')).toEqual([
      { kind: 'operator', key: 'folder', value: '//shared', neg: false },
    ])
  })

  it('unescapes a quoted value carrying a literal double quote', () => {
    expect(tokenize('"https://x/a\\"b"')).toEqual([
      { kind: 'text', value: 'https://x/a"b', neg: false },
    ])
  })
})

describe('stringifyTokens', () => {
  it('round-trips simple tokens', () => {
    const t: QueryToken[] = [
      { kind: 'tag', value: 'a', neg: false },
      { kind: 'operator', key: 'folder', value: 'b', neg: false },
      { kind: 'text', value: 'c', neg: true },
    ]
    expect(stringifyTokens(t)).toBe('#a folder:b -c')
  })

  it('quotes values containing spaces', () => {
    const t: QueryToken[] = [{ kind: 'operator', key: 'folder', value: 'multi word', neg: false }]
    expect(stringifyTokens(t)).toBe('folder:"multi word"')
  })

  it('quotes tag values containing chars outside [\\w-]', () => {
    const t: QueryToken[] = [{ kind: 'tag', value: 'foo=bar', neg: false }]
    // Without quoting, the unquoted tag regex consumes only `foo` and the rest
    // re-tokenizes as a free-text `=bar`, breaking the filter for tags that
    // contain `=` (or `.`, `:`, etc.).
    expect(stringifyTokens(t)).toBe('#"foo=bar"')
  })

  it('round-trips a tag with special characters through tokenize', () => {
    const original: QueryToken[] = [{ kind: 'tag', value: 'foo=bar', neg: false }]
    const str = stringifyTokens(original)
    expect(tokenize(str)).toEqual(original)
  })

  it('round-trips a url: token unquoted', () => {
    const original: QueryToken[] = [
      { kind: 'operator', key: 'url', value: 'https://example.com/a?b=2&a=1', neg: false },
    ]
    const str = stringifyTokens(original)
    expect(str).toBe('url:https://example.com/a?b=2&a=1')
    expect(tokenize(str)).toEqual(original)
  })

  it('re-quotes a url: value containing spaces and round-trips it', () => {
    const original: QueryToken[] = [
      { kind: 'operator', key: 'url', value: 'https://example.com/a b', neg: false },
    ]
    const str = stringifyTokens(original)
    expect(str).toBe('url:"https://example.com/a b"')
    expect(tokenize(str)).toEqual(original)
  })

  it('round-trips a value containing a double quote instead of splitting it', () => {
    // Regression: `quoteIfNeeded` used to wrap without escaping, so removing any
    // pill rewrote `https://x/a"b` into two tokens that matched nothing.
    const original: QueryToken[] = [{ kind: 'text', value: 'https://x/a"b', neg: false }]
    const str = stringifyTokens(original)
    expect(str).toBe('"https://x/a\\"b"')
    expect(tokenize(str)).toEqual(original)
  })

  it('round-trips a value whose first character is grammar', () => {
    // Regression: `needsQuoting` only guarded a leading known-operator key, so
    // a free-text term starting with `-`, `#` or `'` changed meaning the first
    // time any pill was removed — `-foo` came back as a *negated* token.
    const cases: QueryToken[][] = [
      [{ kind: 'text', value: '-foo', neg: false }],
      [{ kind: 'text', value: '#java', neg: false }],
      [{ kind: 'text', value: "'quoted'", neg: false }],
      [{ kind: 'operator', key: 'folder', value: "'x'", neg: false }],
      [{ kind: 'tag', value: "'x'", neg: false }],
    ]
    for (const original of cases) {
      expect(tokenize(stringifyTokens(original))).toEqual(original)
    }
  })

  it('round-trips an operator value that starts with //', () => {
    const original: QueryToken[] = [
      { kind: 'operator', key: 'folder', value: '//shared', neg: false },
    ]
    expect(tokenize(stringifyTokens(original))).toEqual(original)
  })

  it('round-trips a negated url: token', () => {
    const original: QueryToken[] = [
      { kind: 'operator', key: 'url', value: 'https://example.com/a', neg: true },
    ]
    expect(tokenize(stringifyTokens(original))).toEqual(original)
  })
})

describe('toggleToken', () => {
  it('adds a token when absent', () => {
    const result = toggleToken([], { kind: 'tag', value: 'a', neg: false })
    expect(result).toEqual([{ kind: 'tag', value: 'a', neg: false }])
  })

  it('removes an active token on plain toggle', () => {
    const result = toggleToken([{ kind: 'tag', value: 'a', neg: false }], {
      kind: 'tag',
      value: 'a',
      neg: false,
    })
    expect(result).toEqual([])
  })

  it('removes an excluded token on plain toggle (same value, ignoring neg)', () => {
    const result = toggleToken([{ kind: 'tag', value: 'a', neg: true }], {
      kind: 'tag',
      value: 'a',
      neg: false,
    })
    expect(result).toEqual([])
  })

  it('switches to excluded with the exclude modifier', () => {
    const result = toggleToken(
      [{ kind: 'tag', value: 'a', neg: false }],
      { kind: 'tag', value: 'a', neg: false },
      'exclude',
    )
    expect(result).toEqual([{ kind: 'tag', value: 'a', neg: true }])
  })
})

describe('matchesTokens', () => {
  const ctx: MatchContext = {
    tagNamesById: new Map([
      ['t1', 'quarkus'],
      ['t2', 'vue'],
    ]),
    folderName: 'work',
    ancestorFolderNames: new Set(['work', 'projects', 'home']),
    ancestorFolderIds: new Set(['f-work', 'f-projects', 'f-home']),
  }
  const bookmark: MatchableBookmark = {
    data: {
      title: 'Quarkus guide',
      url: 'https://quarkus.io',
      description: 'reactive framework',
      tagIds: new Set(['t1']),
    },
  }

  it('matches an empty token list', () => {
    expect(matchesTokens(bookmark, [], ctx)).toBe(true)
  })

  it('matches a tag token by tag name', () => {
    expect(matchesTokens(bookmark, [{ kind: 'tag', value: 'quarkus', neg: false }], ctx)).toBe(true)
    expect(matchesTokens(bookmark, [{ kind: 'tag', value: 'react', neg: false }], ctx)).toBe(false)
  })

  it('matches a negated tag token', () => {
    expect(matchesTokens(bookmark, [{ kind: 'tag', value: 'vue', neg: true }], ctx)).toBe(true)
    expect(matchesTokens(bookmark, [{ kind: 'tag', value: 'quarkus', neg: true }], ctx)).toBe(false)
  })

  it('matches tag: operator as an alias for #tag (exact, case-insensitive)', () => {
    expect(
      matchesTokens(bookmark, [{ kind: 'operator', key: 'tag', value: 'quarkus', neg: false }], ctx),
    ).toBe(true)
    expect(
      matchesTokens(bookmark, [{ kind: 'operator', key: 'tag', value: 'QUARKUS', neg: false }], ctx),
    ).toBe(true)
    expect(
      matchesTokens(bookmark, [{ kind: 'operator', key: 'tag', value: 'react', neg: false }], ctx),
    ).toBe(false)
    // Substring must NOT match — tag matching is exact, unlike folder:.
    expect(
      matchesTokens(bookmark, [{ kind: 'operator', key: 'tag', value: 'quark', neg: false }], ctx),
    ).toBe(false)
  })

  it('matches folder operator (flat substring on direct folder name)', () => {
    expect(
      matchesTokens(
        bookmark,
        [{ kind: 'operator', key: 'folder', value: 'work', neg: false }],
        ctx,
      ),
    ).toBe(true)
    expect(
      matchesTokens(
        bookmark,
        [{ kind: 'operator', key: 'folder', value: 'home', neg: false }],
        ctx,
      ),
    ).toBe(false)
  })

  it('matches under operator on any ancestor folder name', () => {
    // The bookmark sits in folder `work`, whose chain is work → projects → home.
    expect(
      matchesTokens(bookmark, [{ kind: 'operator', key: 'under', value: 'work', neg: false }], ctx),
    ).toBe(true)
    expect(
      matchesTokens(
        bookmark,
        [{ kind: 'operator', key: 'under', value: 'projects', neg: false }],
        ctx,
      ),
    ).toBe(true)
    expect(
      matchesTokens(bookmark, [{ kind: 'operator', key: 'under', value: 'home', neg: false }], ctx),
    ).toBe(true)
    expect(
      matchesTokens(
        bookmark,
        [{ kind: 'operator', key: 'under', value: 'archive', neg: false }],
        ctx,
      ),
    ).toBe(false)
  })

  it('under operator does not match an unfiled bookmark', () => {
    const unfiledCtx: MatchContext = {
      ...ctx,
      folderName: null,
      ancestorFolderNames: new Set(),
      ancestorFolderIds: new Set(),
    }
    expect(
      matchesTokens(
        bookmark,
        [{ kind: 'operator', key: 'under', value: 'work', neg: false }],
        unfiledCtx,
      ),
    ).toBe(false)
  })

  it('under operator matches an ancestor by folder id (click-path encoding)', () => {
    expect(
      matchesTokens(
        bookmark,
        [{ kind: 'operator', key: 'under', value: 'f-projects', neg: false }],
        ctx,
      ),
    ).toBe(true)
    expect(
      matchesTokens(
        bookmark,
        [{ kind: 'operator', key: 'under', value: 'f-archive', neg: false }],
        ctx,
      ),
    ).toBe(false)
  })

  it('matches note operator on description', () => {
    expect(
      matchesTokens(
        bookmark,
        [{ kind: 'operator', key: 'note', value: 'reactive', neg: false }],
        ctx,
      ),
    ).toBe(true)
  })

  it('treats unknown operators as invalid — they match nothing', () => {
    expect(
      matchesTokens(
        bookmark,
        [{ kind: 'operator', key: 'bogus', value: 'value', neg: false }],
        ctx,
      ),
    ).toBe(false)
  })

  it('negation does not rescue an invalid token — -bogus:x and -url:??? match nothing', () => {
    expect(
      matchesTokens(bookmark, [{ kind: 'operator', key: 'bogus', value: 'x', neg: true }], ctx),
    ).toBe(false)
    expect(
      matchesTokens(bookmark, [{ kind: 'operator', key: 'url', value: '???', neg: true }], ctx),
    ).toBe(false)
  })

  it('matches free text against title / url / description', () => {
    expect(matchesTokens(bookmark, [{ kind: 'text', value: 'guide', neg: false }], ctx)).toBe(true)
    expect(matchesTokens(bookmark, [{ kind: 'text', value: 'absent', neg: false }], ctx)).toBe(
      false,
    )
  })
})

// The `url:` exact-URL operator. All cases compare through the shared
// normalizeUrl contract (lowercased scheme/host, sorted query params,
// tracking parameters kept).
describe('matchesTokens with url:', () => {
  const ctx: MatchContext = {
    tagNamesById: new Map(),
    folderName: null,
    ancestorFolderNames: new Set(),
    ancestorFolderIds: new Set(),
  }
  function bmWithUrl(url: string | null): MatchableBookmark {
    return { data: { title: 't', url, description: null } }
  }
  function urlToken(value: string, neg = false): QueryToken {
    return { kind: 'operator', key: 'url', value, neg }
  }

  it('matches a stored URL that differs in host case, trailing slash, or fragment', () => {
    const query = 'https://example.com/a'
    expect(matchesTokens(bmWithUrl('https://Example.com/a/'), [urlToken(query)], ctx)).toBe(true)
    expect(matchesTokens(bmWithUrl('https://example.com/a#top'), [urlToken(query)], ctx)).toBe(true)
    expect(matchesTokens(bmWithUrl('https://example.com/a'), [urlToken(query)], ctx)).toBe(true)
  })

  it('does not match a deeper path or a different query string', () => {
    const query = 'https://example.com/a'
    expect(matchesTokens(bmWithUrl('https://example.com/a/b'), [urlToken(query)], ctx)).toBe(false)
    expect(
      matchesTokens(bmWithUrl('https://example.com/a?utm_source=x'), [urlToken(query)], ctx),
    ).toBe(false)
  })

  it('keeps tracking parameters significant on both sides', () => {
    expect(
      matchesTokens(bmWithUrl('https://example.com/a?utm_source=x'), [urlToken('https://example.com/a?utm_source=x')], ctx),
    ).toBe(true)
    expect(
      matchesTokens(bmWithUrl('https://example.com/a'), [urlToken('https://example.com/a?utm_source=x')], ctx),
    ).toBe(false)
  })

  it('sorts query parameters before comparing', () => {
    expect(
      matchesTokens(bmWithUrl('https://example.com/a?a=1&b=2'), [urlToken('https://example.com/a?b=2&a=1')], ctx),
    ).toBe(true)
  })

  it('compares path case-sensitively while scheme/host ignore case', () => {
    expect(matchesTokens(bmWithUrl('https://example.com/A'), [urlToken('https://example.com/a')], ctx)).toBe(false)
    expect(matchesTokens(bmWithUrl('https://example.com/A'), [urlToken('https://Example.com/A')], ctx)).toBe(true)
  })

  it('negation returns the complement', () => {
    const exact = bmWithUrl('https://Example.com/a/')
    const deeper = bmWithUrl('https://example.com/a/b')
    expect(matchesTokens(exact, [urlToken('https://example.com/a', true)], ctx)).toBe(false)
    expect(matchesTokens(deeper, [urlToken('https://example.com/a', true)], ctx)).toBe(true)
  })

  it('a valid negated url that matches nothing keeps the bookmark (exclude-nothing)', () => {
    // -url:<valid but absent> is legitimate exclusion semantics — only
    // invalid syntax is hard-failed regardless of negation.
    expect(
      matchesTokens(
        bmWithUrl('https://example.com/a'),
        [urlToken('https://nowhere.example.com', true)],
        ctx,
      ),
    ).toBe(true)
  })

  it('an unparseable value is invalid syntax and matches nothing', () => {
    expect(matchesTokens(bmWithUrl('https://example.com/a'), [urlToken('???')], ctx)).toBe(false)
    expect(matchesTokens(bmWithUrl('https://example.com/a'), [urlToken('example.com/a')], ctx)).toBe(false)
  })

  it('a malformed authority is invalid: the flag and the matcher agree', () => {
    // `https://two words` passes the bare-token prefix check but fails the
    // URL round-trip — it must be flagged AND match nothing, never silently
    // one or the other.
    const token = urlToken('https://two words')
    expect(matchesTokens(bmWithUrl('https://two words'), [token], ctx)).toBe(false)
    expect(isInvalidToken(token)).toBe(true)
  })

  it('matches non-hierarchical schemes stored via API or import (mailto:)', () => {
    expect(
      matchesTokens(bmWithUrl('mailto:dev@example.com'), [urlToken('mailto:dev@example.com')], ctx),
    ).toBe(true)
    expect(matchesTokens(bmWithUrl('https://example.com/a'), [urlToken('mailto:x@y.de')], ctx)).toBe(
      false,
    )
  })

  it('a bookmark without a URL never matches', () => {
    expect(matchesTokens(bmWithUrl(null), [urlToken('https://example.com/a')], ctx)).toBe(false)
  })

  it('combines with other tokens using AND (UC-070 BR-081)', () => {
    const b: MatchableBookmark = {
      data: { title: 'Guide', url: 'https://example.com/a', description: null },
    }
    const tokens: QueryToken[] = [
      urlToken('https://example.com/a'),
      { kind: 'text', value: 'guide', neg: false },
    ]
    expect(matchesTokens(b, tokens, ctx)).toBe(true)
    expect(matchesTokens(b, [...tokens, { kind: 'text', value: 'absent', neg: false }], ctx)).toBe(
      false,
    )
  })
})

describe('isKnownOperator / isInvalidToken', () => {
  it('knows the documented operator set', () => {
    for (const key of ['tag', 'folder', 'under', 'url', 'note', 'created', 'property']) {
      expect(isKnownOperator(key)).toBe(true)
    }
    expect(isKnownOperator('bogus')).toBe(false)
    expect(isKnownOperator('https')).toBe(false)
    expect(isKnownOperator('URL')).toBe(true) // lookup is case-insensitive like the tokenizer
  })

  it('flags unknown operator keys as invalid', () => {
    expect(isInvalidToken({ kind: 'operator', key: 'bogus', value: 'v', neg: false })).toBe(true)
    expect(isInvalidToken({ kind: 'operator', key: 'zzz', value: 'v', neg: false })).toBe(true)
  })

  it('flags a url: token whose value is not an absolute URL', () => {
    expect(isInvalidToken({ kind: 'operator', key: 'url', value: '???', neg: false })).toBe(true)
    expect(isInvalidToken({ kind: 'operator', key: 'url', value: '', neg: false })).toBe(true)
    expect(
      isInvalidToken({ kind: 'operator', key: 'url', value: 'https://example.com/a', neg: false }),
    ).toBe(false)
  })

  it('flags a created: token whose value does not parse as a date', () => {
    expect(isInvalidToken({ kind: 'operator', key: 'created', value: 'garbage', neg: false })).toBe(true)
    expect(isInvalidToken({ kind: 'operator', key: 'created', value: '2026-13-99', neg: false })).toBe(true)
    // Every implemented form stays valid, with and without comparators.
    // (Note: `YYYY-MM` / `YYYY` are NOT implemented — only full dates,
    // German dates, and `today` offsets — despite BR-084's older wording.)
    for (const v of ['2026-05-16', '1.5.2026', '>today', '>today-30d', '<2026-05-16', 'today']) {
      expect(isInvalidToken({ kind: 'operator', key: 'created', value: v, neg: false })).toBe(false)
    }
  })

  it('flags a property: token with a syntactically unparseable payload', () => {
    expect(isInvalidToken({ kind: 'operator', key: 'property', value: '???', neg: false })).toBe(true)
    expect(isInvalidToken({ kind: 'operator', key: 'property', value: '=draft', neg: false })).toBe(true)
    // Bare keys are valid existence checks — unknown names are a transparent
    // miss (no match, no flag), not invalid syntax.
    expect(isInvalidToken({ kind: 'operator', key: 'property', value: 'status', neg: false })).toBe(false)
    expect(
      isInvalidToken({ kind: 'operator', key: 'property', value: 'status=draft', neg: false }),
    ).toBe(false)
  })

  it('does not flag text or tag tokens, or valid operators', () => {
    expect(isInvalidToken({ kind: 'text', value: 'https://example.com', neg: false })).toBe(false)
    expect(isInvalidToken({ kind: 'tag', value: 'x', neg: false })).toBe(false)
    expect(
      isInvalidToken({ kind: 'operator', key: 'folder', value: 'work', neg: false }),
    ).toBe(false)
  })
})

// UC-070 BR-081: `match:` is a query-level setting for how FREE-TEXT terms
// combine. Operators always AND; exclusions are never ORed.
describe('matchesTokens with match: (BR-081)', () => {
  const ctx: MatchContext = {
    tagNamesById: new Map([['t1', 'java']]),
    folderName: 'work',
    ancestorFolderNames: new Set(['work']),
    ancestorFolderIds: new Set(),
  }
  function bm(title: string, tagIds: string[] = []): MatchableBookmark {
    return {
      data: { title, url: null, description: null, tagIds: new Set(tagIds) },
    }
  }
  const or: QueryToken = { kind: 'operator', key: 'match', value: 'or', neg: false }
  const text = (value: string, neg = false): QueryToken => ({ kind: 'text', value, neg })

  it('satisfies a bookmark matching any one free-text term', () => {
    const tokens = [or, text('quarkus'), text('hibernate')]
    expect(matchesTokens(bm('Quarkus guide'), tokens, ctx)).toBe(true)
    expect(matchesTokens(bm('Hibernate guide'), tokens, ctx)).toBe(true)
    expect(matchesTokens(bm('Spring guide'), tokens, ctx)).toBe(false)
  })

  it('still ANDs the same terms without the mode token', () => {
    const tokens = [text('quarkus'), text('hibernate')]
    expect(matchesTokens(bm('Quarkus guide'), tokens, ctx)).toBe(false)
    expect(matchesTokens(bm('Quarkus and Hibernate'), tokens, ctx)).toBe(true)
  })

  it('keeps structured operators ANDed across the OR', () => {
    const tokens: QueryToken[] = [
      { kind: 'tag', value: 'java', neg: false },
      or,
      text('quarkus'),
      text('hibernate'),
    ]
    expect(matchesTokens(bm('Quarkus guide', ['t1']), tokens, ctx)).toBe(true)
    // The tag is not part of the OR — losing it loses the bookmark.
    expect(matchesTokens(bm('Quarkus guide'), tokens, ctx)).toBe(false)
  })

  it('applies wherever the mode token sits, and lets the last one win', () => {
    const and: QueryToken = { kind: 'operator', key: 'match', value: 'and', neg: false }
    expect(matchesTokens(bm('Quarkus guide'), [text('quarkus'), text('hibernate'), or], ctx)).toBe(
      true,
    )
    expect(matchesTokens(bm('Quarkus guide'), [or, text('quarkus'), text('hibernate'), and], ctx))
      .toBe(false)
  })

  it('keeps exclusions unconditional in OR mode', () => {
    const tokens = [or, text('quarkus'), text('hibernate'), text('draft', true)]
    expect(matchesTokens(bm('Quarkus guide'), tokens, ctx)).toBe(true)
    // A positive term hit, but the exclusion still removes the bookmark.
    expect(matchesTokens(bm('Quarkus guide draft'), tokens, ctx)).toBe(false)
  })

  it('constrains nothing when there are no free-text terms to combine', () => {
    const tokens: QueryToken[] = [{ kind: 'tag', value: 'java', neg: false }, or]
    expect(matchesTokens(bm('anything', ['t1']), tokens, ctx)).toBe(true)
    expect(matchesTokens(bm('anything'), tokens, ctx)).toBe(false)
  })

  it('accepts and/or case-insensitively and flags anything else', () => {
    for (const v of ['or', 'OR', 'and', 'And']) {
      expect(isInvalidToken({ kind: 'operator', key: 'match', value: v, neg: false })).toBe(false)
    }
    expect(isInvalidToken({ kind: 'operator', key: 'match', value: 'xor', neg: false })).toBe(true)
    expect(isInvalidToken({ kind: 'operator', key: 'match', value: '', neg: false })).toBe(true)
    // A mode cannot be negated — flag it rather than guess what it means.
    expect(isInvalidToken({ kind: 'operator', key: 'match', value: 'or', neg: true })).toBe(true)
  })

  it('tokenizes and round-trips the mode token', () => {
    expect(tokenize('match:OR')).toEqual([
      { kind: 'operator', key: 'match', value: 'OR', neg: false },
    ])
    const original: QueryToken[] = [{ kind: 'operator', key: 'match', value: 'or', neg: false }]
    expect(tokenize(stringifyTokens(original))).toEqual(original)
  })
})

describe('buildAncestorSets', () => {
  const namesById = new Map([
    ['f1', 'root'],
    ['f2', 'child'],
    ['f3', 'grandchild'],
  ])
  const parentById = new Map<string, string | null>([
    ['f2', 'f1'],
    ['f3', 'f2'],
  ])

  it('collects ancestor names and ids from leaf to root', () => {
    const result = buildAncestorSets('f3', namesById, parentById)
    expect(result.ids).toEqual(new Set(['f3', 'f2', 'f1']))
    expect(result.names).toEqual(new Set(['grandchild', 'child', 'root']))
  })

  it('returns only self for a root folder', () => {
    const result = buildAncestorSets('f1', namesById, parentById)
    expect(result.ids).toEqual(new Set(['f1']))
    expect(result.names).toEqual(new Set(['root']))
  })

  it('handles a folder with no parent entry', () => {
    const result = buildAncestorSets('f1', namesById, new Map())
    expect(result.ids).toEqual(new Set(['f1']))
    expect(result.names).toEqual(new Set(['root']))
  })

  it('breaks cycles in the parent chain', () => {
    const cyclicParent = new Map<string, string | null>([
      ['a', 'b'],
      ['b', 'a'],
    ])
    const result = buildAncestorSets('a', new Map(), cyclicParent)
    expect(result.ids).toEqual(new Set(['a', 'b']))
  })

  it('skips folders with no name entry', () => {
    const result = buildAncestorSets('f3', new Map(), parentById)
    expect(result.ids).toEqual(new Set(['f3', 'f2', 'f1']))
    expect(result.names).toEqual(new Set())
  })
})

// parseCreatedValue unit-level tests live in searchQueryCreated.spec.ts.
// The cases below exercise the integrated matcher (token → bookmark filter).

describe('matchesTokens with created:', () => {
  const ctx: MatchContext = {
    tagNamesById: new Map(),
    folderName: null,
    ancestorFolderNames: new Set(),
    ancestorFolderIds: new Set(),
  }
  function bm(d: Date): MatchableBookmark {
    return {
      data: { title: 't', url: 'u', description: 'd' },
      entityInfo: { timestampErstellt: d },
    }
  }

  it('eq: matches the same day, rejects neighboring days', () => {
    const b = bm(new Date(2026, 4, 16, 14, 0)) // 2026-05-16 14:00
    expect(
      matchesTokens(
        b,
        [{ kind: 'operator', key: 'created', value: '2026-05-16', neg: false }],
        ctx,
      ),
    ).toBe(true)
    expect(
      matchesTokens(
        b,
        [{ kind: 'operator', key: 'created', value: '2026-05-15', neg: false }],
        ctx,
      ),
    ).toBe(false)
    expect(
      matchesTokens(
        b,
        [{ kind: 'operator', key: 'created', value: '2026-05-17', neg: false }],
        ctx,
      ),
    ).toBe(false)
  })

  it('gt: strictly after the day (the day itself is excluded)', () => {
    const b = bm(new Date(2026, 4, 16, 14, 0))
    expect(
      matchesTokens(
        b,
        [{ kind: 'operator', key: 'created', value: '>2026-05-15', neg: false }],
        ctx,
      ),
    ).toBe(true)
    expect(
      matchesTokens(
        b,
        [{ kind: 'operator', key: 'created', value: '>2026-05-16', neg: false }],
        ctx,
      ),
    ).toBe(false)
  })

  it('lt: strictly before the day', () => {
    const b = bm(new Date(2026, 4, 15, 23, 59))
    expect(
      matchesTokens(
        b,
        [{ kind: 'operator', key: 'created', value: '<2026-05-16', neg: false }],
        ctx,
      ),
    ).toBe(true)
    const b2 = bm(new Date(2026, 4, 16, 0, 0))
    expect(
      matchesTokens(
        b2,
        [{ kind: 'operator', key: 'created', value: '<2026-05-16', neg: false }],
        ctx,
      ),
    ).toBe(false)
  })

  it('unparseable date is invalid syntax and matches nothing', () => {
    const b = bm(new Date(2026, 4, 16))
    const token: QueryToken = { kind: 'operator', key: 'created', value: 'garbage', neg: false }
    expect(
      matchesTokens(b, [{ kind: 'operator', key: 'created', value: 'garbage', neg: false }], ctx),
    ).toBe(false)
    expect(isInvalidToken(token)).toBe(true)
  })

  it('bookmark without entityInfo timestamp never matches a created: filter', () => {
    const b: MatchableBookmark = { data: { title: 't', url: 'u' } }
    expect(
      matchesTokens(
        b,
        [{ kind: 'operator', key: 'created', value: '2026-05-16', neg: false }],
        ctx,
      ),
    ).toBe(false)
  })
})

// `compileQuery` resolves everything query-side once so the per-bookmark path
// never re-parses a value. The contract it must keep: same answers as
// `matchesTokens`, and `invalid` agreeing with `isInvalidToken`.
describe('compileQuery', () => {
  const ctx: MatchContext = {
    tagNamesById: new Map([['t1', 'java']]),
    folderName: 'work',
    ancestorFolderNames: new Set(['work']),
    ancestorFolderIds: new Set(),
  }
  const bm = (url: string | null, title = 'x'): MatchableBookmark => ({
    data: { title, url, description: null, tagIds: new Set() },
  })

  it('reports invalid syntax without needing a bookmark, and matches nothing', () => {
    const compiled = compileQuery(tokenize('url:???'))
    expect(compiled.invalid).toBe(true)
    expect(compiled.matches(bm('https://example.com/a'), ctx)).toBe(false)
  })

  it('is not invalid for a well-formed query', () => {
    expect(compileQuery(tokenize('url:https://example.com/a')).invalid).toBe(false)
    expect(compileQuery([]).invalid).toBe(false)
  })

  it('reuses one compilation across many bookmarks', () => {
    const compiled = compileQuery(tokenize('url:https://example.com/a'))
    expect(compiled.matches(bm('https://example.com/a/'), ctx)).toBe(true)
    expect(compiled.matches(bm('https://example.com/b'), ctx)).toBe(false)
    expect(compiled.matches(bm(null), ctx)).toBe(false)
    // …and stays stable when called again in any order.
    expect(compiled.matches(bm('https://example.com/a'), ctx)).toBe(true)
  })

  it('agrees with matchesTokens on every form the grammar has', () => {
    const queries = [
      '#java',
      '-#java',
      'tag:java folder:work',
      'under:work',
      'note:hello',
      'created:>today-30d',
      'url:https://example.com/a',
      'match:or quarkus hibernate',
      'match:or quarkus -draft',
      'bogus:"x y"',
      'quarkus',
    ]
    const bookmarks = [
      bm('https://example.com/a', 'Quarkus guide'),
      bm('https://example.com/b', 'Hibernate draft'),
      bm(null, 'java'),
    ]
    for (const q of queries) {
      const tokens = tokenize(q)
      const compiled = compileQuery(tokens)
      for (const b of bookmarks) {
        expect({ q, hit: compiled.matches(b, ctx) }).toEqual({
          q,
          hit: matchesTokens(b, tokens, ctx),
        })
      }
    }
  })
})

// The syntax-help tooltip names the known operators. Composing it from the
// table is what stops it drifting the way three hand-written locale strings did.
describe('KNOWN_OPERATORS_HINT', () => {
  it('lists every known operator, tags first', () => {
    expect(KNOWN_OPERATORS_HINT.startsWith('#tag, ')).toBe(true)
    for (const def of OPERATOR_DEFS) {
      expect(KNOWN_OPERATORS_HINT).toContain(`${def.key}:`)
    }
  })

  it('names nothing the grammar does not know', () => {
    for (const entry of KNOWN_OPERATORS_HINT.split(', ')) {
      if (entry === '#tag') continue
      expect(isKnownOperator(entry.replace(':', ''))).toBe(true)
    }
  })
})
