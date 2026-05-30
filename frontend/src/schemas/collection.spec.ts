import { describe, it, expect } from 'vitest'
import { collectionCreateSchema, collectionDeleteSchema, collectionShareSchema, collectionUpdateSchema } from './collection'
import { standaloneSchemaTranslator as t } from '@/test-utils/schema'

describe('collectionCreateSchema', () => {
  it('should accept valid name', () => {
    expect(collectionCreateSchema(t).parse({ name: 'My Collection' })).toEqual({ name: 'My Collection' })
  })

  it('should reject empty name', () => {
    expect(() => collectionCreateSchema(t).parse({ name: '' })).toThrow()
  })

  it('should reject name over 255 chars', () => {
    expect(() => collectionCreateSchema(t).parse({ name: 'a'.repeat(256) })).toThrow()
  })

  it('should trim whitespace', () => {
    expect(collectionCreateSchema(t).parse({ name: '  hello  ' }).name).toBe('hello')
  })
})

describe('collectionDeleteSchema', () => {
  it('should accept matching names', () => {
    expect(
      collectionDeleteSchema(t).parse({ confirmName: 'Test', expectedName: 'Test' }),
    ).toEqual({ confirmName: 'Test', expectedName: 'Test' })
  })

  it('should reject mismatched names', () => {
    expect(() =>
      collectionDeleteSchema(t).parse({ confirmName: 'Wrong', expectedName: 'Test' }),
    ).toThrow()
  })

  it('should reject empty confirmName', () => {
    expect(() =>
      collectionDeleteSchema(t).parse({ confirmName: '', expectedName: 'Test' }),
    ).toThrow()
  })
})

describe('collectionShareSchema', () => {
  it('should accept valid email', () => {
    expect(collectionShareSchema(t).parse({ email: 'user@example.com' })).toEqual({
      email: 'user@example.com',
    })
  })

  it('should reject empty email', () => {
    expect(() => collectionShareSchema(t).parse({ email: '' })).toThrow()
  })

  it('should reject invalid email', () => {
    expect(() => collectionShareSchema(t).parse({ email: 'not-email' })).toThrow()
  })
})

describe('collectionUpdateSchema', () => {
  it('should accept valid name with empty allowlist', () => {
    expect(
      collectionUpdateSchema(t).parse({ name: 'My Collection', faviconAllowlist: '', screenshotEnabled: true }),
    ).toEqual({ name: 'My Collection', faviconAllowlist: '', screenshotEnabled: true })
  })

  it('should accept valid name with valid allowlist patterns', () => {
    expect(
      collectionUpdateSchema(t).parse({
        name: 'My Collection',
        faviconAllowlist: '*.mycompany.domain\nintranet.local',
        screenshotEnabled: true,
      }),
    ).toEqual({ name: 'My Collection', faviconAllowlist: '*.mycompany.domain\nintranet.local', screenshotEnabled: true })
  })

  it('should accept allowlist with mixed-case input (validated as lowercase)', () => {
    expect(
      collectionUpdateSchema(t).parse({
        name: 'My Collection',
        faviconAllowlist: 'Intranet.Local',
        screenshotEnabled: false,
      }).faviconAllowlist,
    ).toBe('Intranet.Local')
  })

  it('should reject empty name', () => {
    expect(() =>
      collectionUpdateSchema(t).parse({ name: '', faviconAllowlist: '', screenshotEnabled: false }),
    ).toThrow()
  })

  it('should reject name over 255 chars', () => {
    expect(() =>
      collectionUpdateSchema(t).parse({ name: 'a'.repeat(256), faviconAllowlist: '', screenshotEnabled: false }),
    ).toThrow()
  })

  it('should reject allowlist over 2000 chars', () => {
    expect(() =>
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: 'a'.repeat(2001), screenshotEnabled: false }),
    ).toThrow()
  })

  it('should reject bare wildcard *', () => {
    expect(() =>
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: '*', screenshotEnabled: false }),
    ).toThrow()
  })

  it('should reject bare IPv4 addresses', () => {
    expect(() =>
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: '192.168.1.1', screenshotEnabled: false }),
    ).toThrow()
  })

  it('should reject patterns containing scheme', () => {
    expect(() =>
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: 'https://example.com', screenshotEnabled: false }),
    ).toThrow()
  })

  it('should reject patterns containing path', () => {
    expect(() =>
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: 'example.com/path', screenshotEnabled: false }),
    ).toThrow()
  })

  it('should reject patterns containing colon (port)', () => {
    expect(() =>
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: 'example.com:8080', screenshotEnabled: false }),
    ).toThrow()
  })

  it('should accept wildcard subdomain pattern', () => {
    expect(
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: '*.example.com', screenshotEnabled: true }),
    ).toEqual({ name: 'Valid', faviconAllowlist: '*.example.com', screenshotEnabled: true })
  })

  it('should accept plain domain pattern', () => {
    expect(
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: 'intranet.local', screenshotEnabled: true }),
    ).toEqual({ name: 'Valid', faviconAllowlist: 'intranet.local', screenshotEnabled: true })
  })

  it('should accept multiple valid patterns on separate lines', () => {
    const allowlist = '*.mycompany.domain\nintranet.local\nstaging.internal'
    expect(
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: allowlist, screenshotEnabled: true }),
    ).toEqual({ name: 'Valid', faviconAllowlist: allowlist, screenshotEnabled: true })
  })

  it('should accept allowlist with only whitespace (treated as empty)', () => {
    expect(
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: '   ', screenshotEnabled: false }),
    ).toEqual({ name: 'Valid', faviconAllowlist: '   ', screenshotEnabled: false })
  })
})
