import { describe, it, expect } from 'vitest'
import { propertyDefinitionSaveSchema } from './property'
import { standaloneSchemaTranslator as t } from '@/test-utils/schema'
import { PropertyType } from '@/api/generated'

describe('propertyDefinitionSaveSchema', () => {
  const valid = {
    collectionId: 'col-1',
    name: 'status',
    type: PropertyType.Text,
    sortOrder: 0,
  }

  it('accepts a minimal valid definition', () => {
    expect(propertyDefinitionSaveSchema(t).parse(valid)).toEqual({
      ...valid,
      allowedValues: undefined,
    })
  })

  it('trims the name', () => {
    expect(
      propertyDefinitionSaveSchema(t).parse({ ...valid, name: '  status  ' }).name,
    ).toBe('status')
  })

  it('rejects an empty name', () => {
    expect(() => propertyDefinitionSaveSchema(t).parse({ ...valid, name: '' })).toThrow()
  })

  it('rejects names over 50 chars', () => {
    expect(() =>
      propertyDefinitionSaveSchema(t).parse({ ...valid, name: 'a'.repeat(51) }),
    ).toThrow()
  })

  it('rejects an empty collectionId', () => {
    expect(() =>
      propertyDefinitionSaveSchema(t).parse({ ...valid, collectionId: '' }),
    ).toThrow()
  })

  it('coerces empty allowedValues to undefined for non-select types', () => {
    expect(
      propertyDefinitionSaveSchema(t).parse({ ...valid, allowedValues: '   ' }).allowedValues,
    ).toBeUndefined()
  })

  it('requires allowedValues for SELECT', () => {
    expect(() =>
      propertyDefinitionSaveSchema(t).parse({ ...valid, type: PropertyType.Select }),
    ).toThrow()
    expect(() =>
      propertyDefinitionSaveSchema(t).parse({
        ...valid,
        type: PropertyType.Select,
        allowedValues: '',
      }),
    ).toThrow()
  })

  it('requires allowedValues for MULTI_SELECT', () => {
    expect(() =>
      propertyDefinitionSaveSchema(t).parse({ ...valid, type: PropertyType.MultiSelect }),
    ).toThrow()
  })

  it('accepts allowedValues for SELECT when non-empty', () => {
    const parsed = propertyDefinitionSaveSchema(t).parse({
      ...valid,
      type: PropertyType.Select,
      allowedValues: 'draft, review, published',
    })
    expect(parsed.allowedValues).toBe('draft, review, published')
  })

  it('ignores allowedValues for types that do not need them', () => {
    // Non-select types should accept an allowedValues string without complaint
    // (the matcher just won't look at it). We don't strip the value because the
    // user might be switching types in the form and we want their entry to
    // survive a round-trip until they explicitly clear it.
    const parsed = propertyDefinitionSaveSchema(t).parse({
      ...valid,
      type: PropertyType.Text,
      allowedValues: 'irrelevant',
    })
    expect(parsed.allowedValues).toBe('irrelevant')
  })

  it('rejects an invalid type literal', () => {
    expect(() =>
      propertyDefinitionSaveSchema(t).parse({ ...valid, type: 'not-a-type' }),
    ).toThrow()
  })
})
