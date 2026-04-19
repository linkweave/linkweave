import { describe, it, expect } from 'vitest'
import { bookmarkSaveSchema, bookmarkMoveSchema } from './bookmark'
import { standaloneSchemaTranslator as t } from '@/test-utils/schema'

describe('bookmarkSaveSchema', () => {
  const valid = {
    collectionId: 'col-1',
    title: 'My Bookmark',
    url: 'https://example.com',
  }

  it('should accept valid bookmark data', () => {
    expect(bookmarkSaveSchema(t).parse(valid)).toEqual(valid)
  })

  it('should accept all optional fields', () => {
    const data = {
      ...valid,
      folderId: 'folder-1',
      description: 'A description',
      tagIds: new Set(['tag-1', 'tag-2']),
    }
    expect(bookmarkSaveSchema(t).parse(data)).toEqual(data)
  })

  it('should reject missing title', () => {
    expect(() => bookmarkSaveSchema(t).parse({ ...valid, title: '' })).toThrow()
  })

  it('should reject missing url', () => {
    expect(() => bookmarkSaveSchema(t).parse({ ...valid, url: '' })).toThrow()
  })

  it('should reject invalid url', () => {
    expect(() => bookmarkSaveSchema(t).parse({ ...valid, url: 'not-a-url' })).toThrow()
  })

  it('should reject url without http/https', () => {
    expect(() => bookmarkSaveSchema(t).parse({ ...valid, url: 'ftp://example.com' })).toThrow()
  })

  it('should reject missing collectionId', () => {
    expect(() => bookmarkSaveSchema(t).parse({ title: 'Test', url: 'https://example.com' })).toThrow()
  })

  it('should trim title whitespace', () => {
    const result = bookmarkSaveSchema(t).parse({ ...valid, title: '  hello  ' })
    expect(result.title).toBe('hello')
  })

  it('should normalize empty description to undefined', () => {
    const result = bookmarkSaveSchema(t).parse({ ...valid, description: '' })
    expect(result.description).toBeUndefined()
  })

  it('should preserve non-empty description', () => {
    const result = bookmarkSaveSchema(t).parse({ ...valid, description: 'A note' })
    expect(result.description).toBe('A note')
  })
})

describe('bookmarkMoveSchema', () => {
  it('should accept valid move data', () => {
    const data = { collectionId: 'col-1', folderId: 'folder-1' }
    expect(bookmarkMoveSchema(t).parse(data)).toEqual(data)
  })

  it('should accept move without folderId', () => {
    const data = { collectionId: 'col-1' }
    expect(bookmarkMoveSchema(t).parse(data)).toEqual(data)
  })

  it('should reject missing collectionId', () => {
    expect(() => bookmarkMoveSchema(t).parse({ folderId: 'folder-1' })).toThrow()
  })
})
