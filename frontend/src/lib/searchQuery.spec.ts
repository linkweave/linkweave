import { describe, it, expect } from 'vitest'
import {
  tokenize,
  stringifyTokens,
  toggleToken,
  matchesTokens,
  type QueryToken,
  type MatchContext,
  type MatchableBookmark,
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
      { kind: 'op', key: 'folder', value: 'multi word', neg: false },
    ])
  })

  it('parses a mix of tokens', () => {
    expect(tokenize('#a folder:b -c')).toEqual([
      { kind: 'tag', value: 'a', neg: false },
      { kind: 'op', key: 'folder', value: 'b', neg: false },
      { kind: 'text', value: 'c', neg: true },
    ])
  })

  it('does not crash on unknown operators', () => {
    expect(tokenize('property:foo created:>today')).toEqual([
      { kind: 'op', key: 'property', value: 'foo', neg: false },
      { kind: 'op', key: 'created', value: '>today', neg: false },
    ])
  })
})

describe('stringifyTokens', () => {
  it('round-trips simple tokens', () => {
    const t: QueryToken[] = [
      { kind: 'tag', value: 'a', neg: false },
      { kind: 'op', key: 'folder', value: 'b', neg: false },
      { kind: 'text', value: 'c', neg: true },
    ]
    expect(stringifyTokens(t)).toBe('#a folder:b -c')
  })

  it('quotes values containing spaces', () => {
    const t: QueryToken[] = [{ kind: 'op', key: 'folder', value: 'multi word', neg: false }]
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
    const result = toggleToken(
      [{ kind: 'tag', value: 'a', neg: false }],
      { kind: 'tag', value: 'a', neg: false },
    )
    expect(result).toEqual([])
  })

  it('removes an excluded token on plain toggle (same value, ignoring neg)', () => {
    const result = toggleToken(
      [{ kind: 'tag', value: 'a', neg: true }],
      { kind: 'tag', value: 'a', neg: false },
    )
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
    tagNamesById: new Map([['t1', 'quarkus'], ['t2', 'vue']]),
    folderName: 'work',
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

  it('matches folder operator', () => {
    expect(matchesTokens(bookmark, [{ kind: 'op', key: 'folder', value: 'work', neg: false }], ctx)).toBe(true)
    expect(matchesTokens(bookmark, [{ kind: 'op', key: 'folder', value: 'home', neg: false }], ctx)).toBe(false)
  })

  it('matches note operator on description', () => {
    expect(matchesTokens(bookmark, [{ kind: 'op', key: 'note', value: 'reactive', neg: false }], ctx)).toBe(true)
  })

  it('treats unknown operators as no-op match-all', () => {
    expect(matchesTokens(bookmark, [{ kind: 'op', key: 'property', value: 'foo', neg: false }], ctx)).toBe(true)
  })

  it('matches free text against title / url / description', () => {
    expect(matchesTokens(bookmark, [{ kind: 'text', value: 'guide', neg: false }], ctx)).toBe(true)
    expect(matchesTokens(bookmark, [{ kind: 'text', value: 'absent', neg: false }], ctx)).toBe(false)
  })
})
