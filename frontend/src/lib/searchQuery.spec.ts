import { describe, expect, it } from 'vitest'
import {
  buildAncestorSets,
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

  it('treats unknown operators as no-op match-all', () => {
    expect(
      matchesTokens(
        bookmark,
        [{ kind: 'operator', key: 'unknown', value: 'foo', neg: false }],
        ctx,
      ),
    ).toBe(true)
  })

  it('matches free text against title / url / description', () => {
    expect(matchesTokens(bookmark, [{ kind: 'text', value: 'guide', neg: false }], ctx)).toBe(true)
    expect(matchesTokens(bookmark, [{ kind: 'text', value: 'absent', neg: false }], ctx)).toBe(
      false,
    )
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
