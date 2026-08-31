import { describe, expect, it } from 'vitest'
import { isKnownOperator } from './searchOperators'
import {
  buildAncestorSets,
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

  it('keeps a non-URL key:value token an operator (unknown → invalid, not text)', () => {
    expect(tokenize('bogus:value')).toEqual([
      { kind: 'operator', key: 'bogus', value: 'value', neg: false },
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

  it('an unparseable value is invalid syntax and matches nothing', () => {
    expect(matchesTokens(bmWithUrl('https://example.com/a'), [urlToken('???')], ctx)).toBe(false)
    expect(matchesTokens(bmWithUrl('https://example.com/a'), [urlToken('example.com/a')], ctx)).toBe(false)
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
    expect(isInvalidToken({ kind: 'operator', key: 'match', value: 'OR', neg: false })).toBe(true)
  })

  it('flags a url: token whose value is not an absolute URL', () => {
    expect(isInvalidToken({ kind: 'operator', key: 'url', value: '???', neg: false })).toBe(true)
    expect(isInvalidToken({ kind: 'operator', key: 'url', value: '', neg: false })).toBe(true)
    expect(
      isInvalidToken({ kind: 'operator', key: 'url', value: 'https://example.com/a', neg: false }),
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

  it('unparseable date → no-op match-all (avoid silently hiding everything)', () => {
    const b = bm(new Date(2026, 4, 16))
    expect(
      matchesTokens(b, [{ kind: 'operator', key: 'created', value: 'garbage', neg: false }], ctx),
    ).toBe(true)
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
