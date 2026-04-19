import { describe, it, expect } from 'vitest'
import { collectionCreateSchema, collectionDeleteSchema, collectionShareSchema } from './collection'

describe('collectionCreateSchema', () => {
  it('should accept valid name', () => {
    expect(collectionCreateSchema.parse({ name: 'My Collection' })).toEqual({ name: 'My Collection' })
  })

  it('should reject empty name', () => {
    expect(() => collectionCreateSchema.parse({ name: '' })).toThrow()
  })

  it('should reject name over 255 chars', () => {
    expect(() => collectionCreateSchema.parse({ name: 'a'.repeat(256) })).toThrow()
  })

  it('should trim whitespace', () => {
    expect(collectionCreateSchema.parse({ name: '  hello  ' }).name).toBe('hello')
  })
})

describe('collectionDeleteSchema', () => {
  it('should accept matching names', () => {
    expect(
      collectionDeleteSchema.parse({ confirmName: 'Test', expectedName: 'Test' }),
    ).toEqual({ confirmName: 'Test', expectedName: 'Test' })
  })

  it('should reject mismatched names', () => {
    expect(() =>
      collectionDeleteSchema.parse({ confirmName: 'Wrong', expectedName: 'Test' }),
    ).toThrow()
  })

  it('should reject empty confirmName', () => {
    expect(() =>
      collectionDeleteSchema.parse({ confirmName: '', expectedName: 'Test' }),
    ).toThrow()
  })
})

describe('collectionShareSchema', () => {
  it('should accept valid email', () => {
    expect(collectionShareSchema.parse({ email: 'user@example.com' })).toEqual({
      email: 'user@example.com',
    })
  })

  it('should reject empty email', () => {
    expect(() => collectionShareSchema.parse({ email: '' })).toThrow()
  })

  it('should reject invalid email', () => {
    expect(() => collectionShareSchema.parse({ email: 'not-email' })).toThrow()
  })
})
