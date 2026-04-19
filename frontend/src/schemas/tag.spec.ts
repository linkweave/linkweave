import { describe, it, expect } from 'vitest'
import { tagSaveSchema } from './tag'
import { standaloneSchemaTranslator as t } from '@/test-utils/schema'

describe('tagSaveSchema', () => {
  const valid = { collectionId: 'col-1', name: 'Bug' }

  it('should accept valid tag data', () => {
    expect(tagSaveSchema(t).parse(valid)).toEqual(valid)
  })

  it('should accept tag with color', () => {
    expect(tagSaveSchema(t).parse({ ...valid, color: '#ef4444' })).toEqual({
      ...valid,
      color: '#ef4444',
    })
  })

  it('should accept tag without color', () => {
    expect(tagSaveSchema(t).parse(valid)).toEqual(valid)
  })

  it('should reject empty name', () => {
    expect(() => tagSaveSchema(t).parse({ ...valid, name: '' })).toThrow()
  })

  it('should reject name over 50 chars', () => {
    expect(() => tagSaveSchema(t).parse({ ...valid, name: 'a'.repeat(51) })).toThrow()
  })

  it('should reject invalid hex color', () => {
    expect(() => tagSaveSchema(t).parse({ ...valid, color: 'red' })).toThrow()
  })

  it('should accept empty string color', () => {
    expect(tagSaveSchema(t).parse({ ...valid, color: '' })).toEqual({ ...valid, color: '' })
  })

  it('should trim name whitespace', () => {
    expect(tagSaveSchema(t).parse({ ...valid, name: '  Bug  ' }).name).toBe('Bug')
  })
})
