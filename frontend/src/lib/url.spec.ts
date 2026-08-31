import { ensureUrlProtocol, isAbsoluteUrl, normalizeUrl } from './url'

describe('ensureUrlProtocol', () => {
  it('prepends https:// when no colon present', () => {
    expect(ensureUrlProtocol('example.com')).toBe('https://example.com')
    expect(ensureUrlProtocol('foo.bar/path')).toBe('https://foo.bar/path')
  })

  it('leaves urls with a protocol untouched', () => {
    expect(ensureUrlProtocol('http://example.com')).toBe('http://example.com')
    expect(ensureUrlProtocol('https://example.com')).toBe('https://example.com')
    expect(ensureUrlProtocol('ftp://example.com')).toBe('ftp://example.com')
  })

  it('leaves urls with a port untouched (also has colon)', () => {
    expect(ensureUrlProtocol('localhost:8080')).toBe('localhost:8080')
  })

  it('returns empty for empty/whitespace input', () => {
    expect(ensureUrlProtocol('')).toBe('')
    expect(ensureUrlProtocol('   ')).toBe('')
  })

  it('trims whitespace before checking', () => {
    expect(ensureUrlProtocol('  example.com  ')).toBe('https://example.com')
  })
})

describe('normalizeUrl', () => {
  it('lowercases scheme and host', () => {
    expect(normalizeUrl('HTTPS://Example.COM/Path')).toBe('https://example.com/Path')
  })

  it('treats a bare domain and a trailing root slash as equal', () => {
    expect(normalizeUrl('https://example.com')).toBe('https://example.com')
    expect(normalizeUrl('https://example.com/')).toBe('https://example.com')
  })

  it('strips trailing slashes from a path', () => {
    expect(normalizeUrl('https://example.com/path/')).toBe('https://example.com/path')
  })

  it('drops the fragment and sorts query params', () => {
    expect(normalizeUrl('https://example.com/x?b=2&a=1#frag')).toBe('https://example.com/x?a=1&b=2')
  })
})

describe('isAbsoluteUrl', () => {
  it('accepts scheme + // URLs in any case', () => {
    expect(isAbsoluteUrl('https://example.com/a')).toBe(true)
    expect(isAbsoluteUrl('http://example.com')).toBe(true)
    expect(isAbsoluteUrl('HTTPS://Example.COM/a')).toBe(true)
    expect(isAbsoluteUrl('ftp://files.example.com/x')).toBe(true)
  })

  it('rejects values without a scheme// separator', () => {
    expect(isAbsoluteUrl('example.com/a')).toBe(false)
    expect(isAbsoluteUrl('???')).toBe(false)
    expect(isAbsoluteUrl('')).toBe(false)
    expect(isAbsoluteUrl('url:https://example.com/a')).toBe(false)
  })

  it('rejects scheme-only values like mailto: (no //)', () => {
    expect(isAbsoluteUrl('mailto:foo@bar.com')).toBe(false)
  })
})
