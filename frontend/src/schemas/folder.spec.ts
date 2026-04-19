import { describe, it, expect } from 'vitest'
import { folderSaveSchema } from './folder'
import { standaloneSchemaTranslator as t } from '@/test-utils/schema'

describe('folderSaveSchema', () => {
  const valid = { collectionId: 'col-1', name: 'Dev' }

  it('should accept valid folder data', () => {
    expect(folderSaveSchema(t).parse(valid)).toEqual(valid)
  })

  it('should accept folder with parentId', () => {
    expect(folderSaveSchema(t).parse({ ...valid, parentId: 'parent-1' })).toEqual({
      ...valid,
      parentId: 'parent-1',
    })
  })

  it('should reject empty name', () => {
    expect(() => folderSaveSchema(t).parse({ ...valid, name: '' })).toThrow()
  })

  it('should reject missing collectionId', () => {
    expect(() => folderSaveSchema(t).parse({ name: 'Dev' })).toThrow()
  })

  it('should trim name whitespace', () => {
    expect(folderSaveSchema(t).parse({ ...valid, name: '  Dev  ' }).name).toBe('Dev')
  })
})
