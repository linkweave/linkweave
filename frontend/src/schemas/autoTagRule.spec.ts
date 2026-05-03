import { describe, it, expect } from 'vitest'
import { autoTagRuleSaveSchema } from './autoTagRule'
import { standaloneSchemaTranslator as t } from '@/test-utils/schema'

describe('autoTagRuleSaveSchema', () => {
  const valid = {
    collectionId: 'col-1',
    pattern: '^https://github\\.com/.+/pull/\\d+',
    tagNames: 'pr, github',
    enabled: true,
  }

  it('should accept valid rule data', () => {
    expect(autoTagRuleSaveSchema(t).parse(valid)).toEqual({ ...valid, description: undefined })
  })

  it('should accept rule with description', () => {
    expect(
      autoTagRuleSaveSchema(t).parse({ ...valid, description: 'Matches GitHub PRs' }),
    ).toEqual({ ...valid, description: 'Matches GitHub PRs' })
  })

  it('should accept rule without description', () => {
    expect(autoTagRuleSaveSchema(t).parse(valid)).toEqual({ ...valid, description: undefined })
  })

  it('should treat empty description as undefined', () => {
    expect(autoTagRuleSaveSchema(t).parse({ ...valid, description: '' })).toEqual({
      ...valid,
      description: undefined,
    })
  })

  it('should reject empty pattern', () => {
    expect(() => autoTagRuleSaveSchema(t).parse({ ...valid, pattern: '' })).toThrow()
  })

  it('should reject pattern exceeding 1024 chars', () => {
    expect(() => autoTagRuleSaveSchema(t).parse({ ...valid, pattern: 'a'.repeat(1025) })).toThrow()
  })

  it('should reject invalid regex pattern', () => {
    expect(() => autoTagRuleSaveSchema(t).parse({ ...valid, pattern: '(unclosed' })).toThrow()
  })

  it('should reject empty tagNames', () => {
    expect(() => autoTagRuleSaveSchema(t).parse({ ...valid, tagNames: '' })).toThrow()
  })

  it('should reject tagNames with only commas and spaces', () => {
    expect(() => autoTagRuleSaveSchema(t).parse({ ...valid, tagNames: ' , , ' })).toThrow()
  })

  it('should reject more than 8 tags', () => {
    expect(() =>
      autoTagRuleSaveSchema(t).parse({ ...valid, tagNames: 'a,b,c,d,e,f,g,h,i' }),
    ).toThrow()
  })

  it('should accept exactly 8 tags', () => {
    expect(
      autoTagRuleSaveSchema(t).parse({ ...valid, tagNames: 'a,b,c,d,e,f,g,h' }).tagNames,
    ).toBe('a,b,c,d,e,f,g,h')
  })

  it('should reject tagNames exceeding 512 chars', () => {
    expect(() =>
      autoTagRuleSaveSchema(t).parse({ ...valid, tagNames: 'a'.repeat(513) }),
    ).toThrow()
  })

  it('should reject description exceeding 255 chars', () => {
    expect(() =>
      autoTagRuleSaveSchema(t).parse({ ...valid, description: 'a'.repeat(256) }),
    ).toThrow()
  })

  it('should trim pattern whitespace', () => {
    const result = autoTagRuleSaveSchema(t).parse({ ...valid, pattern: '  ^test  ' })
    expect(result.pattern).toBe('^test')
  })

  it('should reject missing collectionId', () => {
    expect(() =>
      autoTagRuleSaveSchema(t).parse({ ...valid, collectionId: '' }),
    ).toThrow()
  })
})
