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
