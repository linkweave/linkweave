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
      collectionUpdateSchema(t).parse({ name: 'My Collection', faviconAllowlist: '' }),
    ).toEqual({ name: 'My Collection', faviconAllowlist: '' })
  })

  it('should accept valid name with valid allowlist patterns', () => {
    expect(
      collectionUpdateSchema(t).parse({
        name: 'My Collection',
        faviconAllowlist: '*.mycompany.domain\nintranet.local',
      }),
    ).toEqual({ name: 'My Collection', faviconAllowlist: '*.mycompany.domain\nintranet.local' })
  })

  it('should accept allowlist with mixed-case input (validated as lowercase)', () => {
    expect(
      collectionUpdateSchema(t).parse({
        name: 'My Collection',
        faviconAllowlist: 'Intranet.Local',
      }).faviconAllowlist,
    ).toBe('Intranet.Local')
  })

  it('should reject empty name', () => {
    expect(() =>
      collectionUpdateSchema(t).parse({ name: '', faviconAllowlist: '' }),
    ).toThrow()
  })

  it('should reject name over 255 chars', () => {
    expect(() =>
      collectionUpdateSchema(t).parse({ name: 'a'.repeat(256), faviconAllowlist: '' }),
    ).toThrow()
  })

  it('should reject allowlist over 2000 chars', () => {
    expect(() =>
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: 'a'.repeat(2001) }),
    ).toThrow()
  })

  it('should reject bare wildcard *', () => {
    expect(() =>
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: '*' }),
    ).toThrow()
  })

  it('should reject bare IPv4 addresses', () => {
    expect(() =>
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: '192.168.1.1' }),
    ).toThrow()
  })

  it('should reject patterns containing scheme', () => {
    expect(() =>
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: 'https://example.com' }),
    ).toThrow()
  })

  it('should reject patterns containing path', () => {
    expect(() =>
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: 'example.com/path' }),
    ).toThrow()
  })

  it('should reject patterns containing colon (port)', () => {
    expect(() =>
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: 'example.com:8080' }),
    ).toThrow()
  })

  it('should accept wildcard subdomain pattern', () => {
    expect(
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: '*.example.com' }),
    ).toEqual({ name: 'Valid', faviconAllowlist: '*.example.com' })
  })

  it('should accept plain domain pattern', () => {
    expect(
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: 'intranet.local' }),
    ).toEqual({ name: 'Valid', faviconAllowlist: 'intranet.local' })
  })

  it('should accept multiple valid patterns on separate lines', () => {
    const allowlist = '*.mycompany.domain\nintranet.local\nstaging.internal'
    expect(
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: allowlist }),
    ).toEqual({ name: 'Valid', faviconAllowlist: allowlist })
  })

  it('should accept allowlist with only whitespace (treated as empty)', () => {
    expect(
      collectionUpdateSchema(t).parse({ name: 'Valid', faviconAllowlist: '   ' }),
    ).toEqual({ name: 'Valid', faviconAllowlist: '   ' })
  })
})
