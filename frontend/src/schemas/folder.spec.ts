import { describe, it, expect } from 'vitest'
import { folderSaveSchema } from './folder'

describe('folderSaveSchema', () => {
  const valid = { collectionId: 'col-1', name: 'Dev' }

  it('should accept valid folder data', () => {
    expect(folderSaveSchema.parse(valid)).toEqual(valid)
  })

  it('should accept folder with parentId', () => {
    expect(folderSaveSchema.parse({ ...valid, parentId: 'parent-1' })).toEqual({
      ...valid,
      parentId: 'parent-1',
    })
  })

  it('should reject empty name', () => {
    expect(() => folderSaveSchema.parse({ ...valid, name: '' })).toThrow()
  })

  it('should reject missing collectionId', () => {
    expect(() => folderSaveSchema.parse({ name: 'Dev' })).toThrow()
  })

  it('should trim name whitespace', () => {
    expect(folderSaveSchema.parse({ ...valid, name: '  Dev  ' }).name).toBe('Dev')
  })
})
