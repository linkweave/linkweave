// @vitest-environment happy-dom
import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSearchAutocomplete } from './useSearchAutocomplete'

// A pasted URL keeps its substring semantics — the dropdown may only *offer*
// the `url:` conversion, never apply it silently.
describe('useSearchAutocomplete – URL conversion offer', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  function parse(query: string, cursor = query.length) {
    return useSearchAutocomplete().parseQueryForAutoCompl(query, cursor)
  }

  it('offers url:<pasted> for an absolute URL token', () => {
    const q = 'https://example.com/a'
    const result = parse(q)
    expect(result?.mode).toBe('url')
    expect(result?.items).toEqual([
      {
        key: 'url-exact',
        label: 'https://example.com/a',
        insert: 'url:https://example.com/a',
        type: 'url',
        hint: 'opUrlConvert',
        filter: '',
      },
    ])
    expect(result?.range).toEqual([0, q.length])
  })

  it('keeps preceding tokens: only the URL token is replaced', () => {
    const q = '#tag https://Example.com/a/'
    const result = parse(q, q.length)
    expect(result?.mode).toBe('url')
    expect(result?.range).toEqual([5, q.length])
    expect(result?.items[0]?.insert).toBe('url:https://Example.com/a/')
  })

  it('converts the full token when the caret sits inside the URL', () => {
    const q = 'https://example.com/a'
    const result = parse(q, 10) // caret after 'https://ex'
    expect(result?.mode).toBe('url')
    expect(result?.range).toEqual([0, q.length])
    expect(result?.items[0]?.insert).toBe('url:https://example.com/a')
  })

  it('does not offer the conversion for plain text or host-only fragments', () => {
    expect(parse('example.com/a')).toBeNull()
    expect(parse('quarkus')).toBeNull()
  })

  it('does not re-offer when the token is already a url: query', () => {
    expect(parse('url:https://example.com/a')).toBeNull()
  })

  it('does not offer the conversion for a negated URL paste', () => {
    expect(parse('-https://example.com/a')).toBeNull()
  })
})

// `match:` is prefix-discoverable, so the dropdown has to be able to finish
// the token: a bare `match:` is invalid syntax, and the modes are a closed set
// of two (UC-070 BR-081).
describe('useSearchAutocomplete – match: modes', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  function parse(query: string, cursor = query.length) {
    return useSearchAutocomplete().parseQueryForAutoCompl(query, cursor)
  }

  it('offers both modes for a bare match:', () => {
    const result = parse('match:')
    expect(result?.mode).toBe('match-val')
    expect(result?.items).toEqual([
      { key: 'and', label: 'and', insert: 'match:and', type: 'match-val', hint: 'opMatchAnd', filter: '' },
      { key: 'or', label: 'or', insert: 'match:or', type: 'match-val', hint: 'opMatchOr', filter: '' },
    ])
  })

  it('filters the modes by what has been typed', () => {
    expect(parse('match:o')?.items.map((i) => i.key)).toEqual(['or'])
    expect(parse('match:A')?.items.map((i) => i.key)).toEqual(['and'])
    expect(parse('match:xor')?.items).toEqual([])
  })

  it('replaces only the match: token, leaving the rest of the query alone', () => {
    const q = '#java match:o quarkus'
    const result = parse(q, 13) // caret right after `match:o`
    expect(result?.range).toEqual([6, 13])
    expect(result?.items[0]?.insert).toBe('match:or')
  })
})

// Typing a prefix of an operator key offers the key. The fully-typed key must
// too, for operators that have no value list of their own to show.
describe('useSearchAutocomplete – operator discovery', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  function parse(query: string, cursor = query.length) {
    return useSearchAutocomplete().parseQueryForAutoCompl(query, cursor)
  }

  it('offers url: for both a prefix and the complete key', () => {
    expect(parse('ur')?.items.map((i) => i.insert)).toContain('url:')
    // Regression: the exact key was filtered out, so the dropdown vanished at
    // the moment the user finished typing it.
    expect(parse('url')?.items.map((i) => i.insert)).toContain('url:')
  })

  it('still lets a key with its own value list answer first', () => {
    // `match` normalizes to `match:`, whose branch offers the modes rather
    // than the operator key itself.
    expect(parse('match')?.mode).toBe('match-val')
  })
})
