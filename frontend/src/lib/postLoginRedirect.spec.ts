// @vitest-environment happy-dom
import {
  clearPostLoginRedirect,
  consumePostLoginRedirect,
  savePostLoginRedirect,
} from './postLoginRedirect'

beforeEach(() => {
  sessionStorage.clear()
})

describe('postLoginRedirect', () => {
  it('should save and consume an internal route including query and hash', () => {
    savePostLoginRedirect('/collections/col-1?tag=work#top')
    expect(consumePostLoginRedirect()).toBe('/collections/col-1?tag=work#top')
  })

  it('should consume the target only once', () => {
    savePostLoginRedirect('/trashbin')
    expect(consumePostLoginRedirect()).toBe('/trashbin')
    expect(consumePostLoginRedirect()).toBeNull()
  })

  it('should return null when nothing was saved', () => {
    expect(consumePostLoginRedirect()).toBeNull()
  })

  it('should drop the target on clear', () => {
    savePostLoginRedirect('/trashbin')
    clearPostLoginRedirect()
    expect(consumePostLoginRedirect()).toBeNull()
  })

  it('should reject absolute URLs (open redirect)', () => {
    savePostLoginRedirect('https://evil.example/phish')
    expect(consumePostLoginRedirect()).toBeNull()
  })

  it('should reject protocol-relative URLs (open redirect)', () => {
    savePostLoginRedirect('//evil.example/phish')
    expect(consumePostLoginRedirect()).toBeNull()
  })

  it('should reject relative paths without a leading slash', () => {
    savePostLoginRedirect('collections/col-1')
    expect(consumePostLoginRedirect()).toBeNull()
  })

  it('should reject public routes to avoid redirect loops', () => {
    for (const target of ['/login', '/login?foo=bar', '/register', '/privacy']) {
      savePostLoginRedirect(target)
      expect(consumePostLoginRedirect()).toBeNull()
    }
  })

  it('should reject API paths — they are not navigable routes', () => {
    savePostLoginRedirect('/api/auth/me')
    expect(consumePostLoginRedirect()).toBeNull()
  })

  it('should reject a tampered stored value on consume', () => {
    sessionStorage.setItem('linkweave.postLoginRedirect', 'https://evil.example')
    expect(consumePostLoginRedirect()).toBeNull()
  })
})
