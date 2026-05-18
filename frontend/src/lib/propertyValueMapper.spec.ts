import { PropertyType } from '@/api/generated'
import { describe, expect, it } from 'vitest'
import {
  decodePropertyValue,
  encodePropertyValue,
  encodePropertyValueMap,
  type PropertyFormValue,
} from './propertyValueMapper'

describe('encodePropertyValue', () => {
  it('encodes TEXT as valueText', () => {
    expect(encodePropertyValue('d-1', PropertyType.Text, 'hello')).toEqual({
      definitionId: 'd-1',
      valueText: 'hello',
    })
  })

  it('encodes NUMBER as valueNumber', () => {
    expect(encodePropertyValue('d-1', PropertyType.Number, 42)).toEqual({
      definitionId: 'd-1',
      valueNumber: 42,
    })
  })

  it('encodes BOOLEAN as valueBoolean (preserves false)', () => {
    expect(encodePropertyValue('d-1', PropertyType.Boolean, true)).toEqual({
      definitionId: 'd-1',
      valueBoolean: true,
    })
    expect(encodePropertyValue('d-1', PropertyType.Boolean, false)).toEqual({
      definitionId: 'd-1',
      valueBoolean: false,
    })
  })

  it('encodes DATE/SELECT via valueText', () => {
    expect(encodePropertyValue('d-1', PropertyType.Date, '2026-05-17')?.valueText).toBe(
      '2026-05-17',
    )
    expect(encodePropertyValue('d-1', PropertyType.Select, 'draft')?.valueText).toBe('draft')
  })

  it('encodes MULTI_SELECT as comma-joined valueText', () => {
    expect(encodePropertyValue('d-1', PropertyType.MultiSelect, ['a', 'b'])?.valueText).toBe('a,b')
  })

  it('returns null for unset values', () => {
    expect(encodePropertyValue('d-1', PropertyType.Text, undefined)).toBeNull()
    expect(encodePropertyValue('d-1', PropertyType.Text, '')).toBeNull()
    expect(encodePropertyValue('d-1', PropertyType.Number, NaN)).toBeNull()
    expect(encodePropertyValue('d-1', PropertyType.MultiSelect, [])).toBeNull()
  })

  it('returns null for type mismatch (caller bug)', () => {
    expect(encodePropertyValue('d-1', PropertyType.Number, 'not-a-number')).toBeNull()
    expect(encodePropertyValue('d-1', PropertyType.Boolean, 1)).toBeNull()
  })
})

describe('decodePropertyValue', () => {
  it('returns undefined for missing entry', () => {
    expect(decodePropertyValue(PropertyType.Text, undefined)).toBeUndefined()
  })

  it('round-trips text / select / date via valueText', () => {
    expect(decodePropertyValue(PropertyType.Text, { definitionId: 'd', valueText: 'x' })).toBe('x')
    expect(
      decodePropertyValue(PropertyType.Select, { definitionId: 'd', valueText: 'draft' }),
    ).toBe('draft')
    expect(
      decodePropertyValue(PropertyType.Date, { definitionId: 'd', valueText: '2026-05-17' }),
    ).toBe('2026-05-17')
  })

  it('splits MULTI_SELECT csv back into an array, trimmed', () => {
    expect(
      decodePropertyValue(PropertyType.MultiSelect, {
        definitionId: 'd',
        valueText: 'a, b , c',
      }),
    ).toEqual(['a', 'b', 'c'])
  })

  it('preserves number values', () => {
    expect(decodePropertyValue(PropertyType.Number, { definitionId: 'd', valueNumber: 7 })).toBe(7)
  })

  it('preserves boolean values (incl. false)', () => {
    expect(
      decodePropertyValue(PropertyType.Boolean, { definitionId: 'd', valueBoolean: true }),
    ).toBe(true)
    expect(
      decodePropertyValue(PropertyType.Boolean, { definitionId: 'd', valueBoolean: false }),
    ).toBe(false)
  })
})

describe('encodePropertyValueMap', () => {
  const types = new Map<string, PropertyType>([
    ['d-text', PropertyType.Text],
    ['d-num', PropertyType.Number],
    ['d-bool', PropertyType.Boolean],
  ])

  it('keeps only entries that encode to a payload', () => {
    const result = encodePropertyValueMap(
      new Map<string, PropertyFormValue>([
        ['d-text', 'hello'],
        ['d-num', 5],
        ['d-bool', false],
        ['d-text-empty', ''], // valid TEXT def but empty value → dropped
      ]),
      types,
    )
    expect(result).toHaveLength(3)
    expect(result.find((v) => v.definitionId === 'd-bool')?.valueBoolean).toBe(false)
  })

  it('drops entries with no matching definition', () => {
    const result = encodePropertyValueMap(
      new Map<string, PropertyFormValue>([['d-unknown', 'value']]),
      types,
    )
    expect(result).toEqual([])
  })
})
